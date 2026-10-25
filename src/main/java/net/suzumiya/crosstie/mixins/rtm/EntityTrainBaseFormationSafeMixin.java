package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * {@link EntityTrainBase#readFormationData(NBTTagCompound)} において、
 * 編成情報の読み込み時に発生する配列長不一致やインデックス超過を安全に補正・回復する Mixin。
 *
 * <p>Issue #87 の修正:
 * サーバーから受信した {@code PacketNBT} をクライアントが処理する際、
 * 既存の {@link Formation} の配列長が NBT に格納された両数や車両位置より小さい場合でも
 * 配列を自動拡張して正常に編成へ登録できるようにする。
 */
@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainBaseFormationSafeMixin {

    @Shadow
    private Formation formation;

    @Inject(method = "readFormationData", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$safeReadFormationData(NBTTagCompound nbt, CallbackInfo ci) {
        if (nbt == null || !nbt.hasKey("FormationId")) {
            // FormationId が存在しない場合はデフォルトの1両編成を作成
            this.formation = FormationManager.getInstance().createNewFormation((EntityTrainBase) (Object) this);
            ci.cancel();
            return;
        }

        long id = nbt.getLong("FormationId");
        int pos = Math.max(0, nbt.getByte("EntryPos"));
        byte dir = nbt.getByte("EntryDir");
        int size = Math.max(pos + 1, nbt.getInteger("FormationSize"));

        Formation f0 = FormationManager.getInstance().getFormation(id);
        if (f0 == null) {
            this.formation = FormationManager.getInstance().createNewFormation(
                    (EntityTrainBase) (Object) this, id, (byte) pos, dir, size
            );
        } else {
            // 既存の編成の配列長が足りない場合は安全に拡張
            if (f0.entries == null) {
                f0.entries = new jp.ngt.rtm.entity.train.util.FormationEntry[Math.max(size, pos + 1)];
            } else if (f0.entries.length < size || pos >= f0.entries.length) {
                int requiredLength = Math.max(size, pos + 1);
                f0.entries = Arrays.copyOf(f0.entries, requiredLength);
            }
            this.formation = f0;
            f0.setTrain((EntityTrainBase) (Object) this, pos, dir);
        }
        ci.cancel();
    }
}

