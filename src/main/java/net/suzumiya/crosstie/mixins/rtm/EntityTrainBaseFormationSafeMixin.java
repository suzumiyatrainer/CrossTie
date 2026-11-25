package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.minecraft.nbt.NBTTagCompound;
import net.suzumiya.crosstie.utils.FormationSyncHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * {@link EntityTrainBase#readFormationData(NBTTagCompound)} および {@link EntityTrainBase#getFormation()} において、
 * 編成情報の読み込み時に発生する配列長不一致やインデックス超過を安全に補正し、
 * クライアント・サーバー間のパケット到着順ズレによる古い編成の上書きを防止する Mixin。
 */
@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainBaseFormationSafeMixin {

    @Shadow
    private Formation formation;

    /**
     * クライアント側で getFormation() が呼ばれた際、PacketFormation 受信時点で未解決だった車両の
     * 編成参照および FormationEntry を遅延解決して最新状態に同期する。
     */
    @Inject(method = "getFormation", at = @At("HEAD"), require = 0)
    private void crosstie$ensureClientFormationSynced(CallbackInfoReturnable<Formation> cir) {
        EntityTrainBase self = (EntityTrainBase) (Object) this;
        if (self.worldObj != null && self.worldObj.isRemote) {
            Formation resolved = FormationSyncHelper.resolveClientTrainFormation(self, this.formation);
            if (resolved != null && resolved != this.formation) {
                this.formation = resolved;
            }
        }
    }

    @Inject(method = "readFormationData", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$safeReadFormationData(NBTTagCompound nbt, CallbackInfo ci) {
        EntityTrainBase self = (EntityTrainBase) (Object) this;
        boolean isClient = (self.worldObj != null && self.worldObj.isRemote) || !NGTUtil.isServer();

        if (nbt == null || !nbt.hasKey("FormationId")) {
            if (isClient) {
                // クライアント側で spawnTrain 途中の初期 PacketNBT (FormationId なし) を受信した場合、
                // 既存の有効な編成や PacketFormation の予約状態を偽の1両編成で上書きしない。
                Formation pendingFormation = FormationSyncHelper.resolveClientTrainFormation(self, this.formation);
                if (pendingFormation != null) {
                    this.formation = pendingFormation;
                }
            } else {
                // サーバー側で FormationId が存在しない場合はデフォルトの1両編成を作成
                this.formation = FormationManager.getInstance().createNewFormation(self);
            }
            ci.cancel();
            return;
        }

        long id = nbt.getLong("FormationId");
        int pos = Math.max(0, nbt.getByte("EntryPos"));
        byte dir = nbt.getByte("EntryDir");
        int size = Math.max(pos + 1, nbt.getInteger("FormationSize"));

        if (isClient) {
            // クライアント側で既に PacketFormation により確定した編成に登録されている場合、
            // 後から遅延到着した古い PacketNBT で上書き・破壊するのを防ぐ。
            // 注意: connectTrain() では連結する側 (this) の FormationId は変わらずサイズだけ増えるため、
            // authoritative.id == id であっても authoritative.size() > size なら古い PacketNBT である。
            Formation authoritative = FormationSyncHelper.resolveClientTrainFormation(self, this.formation);
            if (authoritative != null) {
                boolean hasAuthoritative = FormationSyncHelper.hasAuthoritativeMapping(self.getEntityId());
                if (authoritative.size() > size) {
                    this.formation = authoritative;
                    ci.cancel();
                    return;
                }
                if (hasAuthoritative && authoritative.id == id && authoritative.getEntry(self) != null) {
                    this.formation = authoritative;
                    ci.cancel();
                    return;
                }
            }
        }

        Formation f0 = FormationManager.getInstance().getFormation(id);
        if (f0 == null) {
            this.formation = FormationManager.getInstance().createNewFormation(
                    self, id, (byte) pos, dir, size
            );
            if (isClient) {
                FormationSyncHelper.recordNbtEntryIfAbsent(id, self.getEntityId(), pos, dir);
                FormationSyncHelper.notifyClientFormationUpdated();
            }
        } else {
            // 既存の編成の配列長が足りない場合は安全に厳密サイズで拡張
            if (f0.entries == null) {
                f0.entries = new FormationEntry[Math.max(size, pos + 1)];
            } else if (f0.entries.length < size || pos >= f0.entries.length) {
                int requiredLength = Math.max(size, pos + 1);
                f0.entries = Arrays.copyOf(f0.entries, requiredLength);
            }
            this.formation = f0;
            if (isClient) {
                // クライアント側ではすでに同一車両のエントリが設定済みなら向き・位置のみ更新し、
                // 不要なインスタンス再生成を避ける
                FormationEntry existing = f0.getEntry(self);
                if (existing != null && existing.entryId == pos && f0.entries[pos] == existing) {
                    existing.dir = dir;
                } else {
                    f0.setTrain(self, pos, dir);
                }
                FormationSyncHelper.recordNbtEntryIfAbsent(id, self.getEntityId(), pos, dir);
                FormationSyncHelper.notifyClientFormationUpdated();
            } else {
                f0.setTrain(self, pos, dir);
            }
        }
        ci.cancel();
    }
}
