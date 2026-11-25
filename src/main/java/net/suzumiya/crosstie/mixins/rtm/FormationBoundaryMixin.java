package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.suzumiya.crosstie.utils.FormationSyncHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * {@link Formation} の配列境界外アクセス (ArrayIndexOutOfBoundsException) を防止し、
 * クライアント側での欠落エントリの自動自己修復 (Self-Healing) と正確な位置配置を行う Mixin。
 */
@Mixin(value = Formation.class, remap = false)
public abstract class FormationBoundaryMixin {

    @Shadow
    public long id;

    @Shadow
    public FormationEntry[] entries;

    @Shadow
    private byte direction;

    /**
     * Formation.readFromNBT において:
     * 1. NGTUtil.getClientWorld() が null のタイミングでも NPE で落ちないように保護する。
     * 2. ループ変数 i ではなく EntryPos (本来の号車番号) のインデックスへ正確に格納する。
     * 3. まだクライアントにスポーンしていない車両のエントリを FormationSyncHelper に記録し、
     *    スポーン後や getFormation() / get() 呼び出し時に自動復元できるようにする。
     * 4. サーバー側で一部車両のみロード済みの中間パケットを受信した際、
     *    クライアント側で既に解決済みだった別スロットの車両参照を不用意に消失させない。
     */
    @Inject(method = "readFromNBT", at = @At("HEAD"), cancellable = true, require = 0)
    private static void crosstie$safeReadFromNBT(NBTTagCompound nbt, boolean withEntries,
            CallbackInfoReturnable<Formation> cir) {
        if (nbt == null) {
            cir.setReturnValue(null);
            return;
        }

        long fid = nbt.getLong("FormationId");
        int num = Math.max(0, nbt.getInteger("Size"));
        NBTTagList tagList = withEntries ? nbt.getTagList("Entries", 10) : null;
        if (tagList != null) {
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);
                int pos = tag.getByte("EntryPos");
                if (pos + 1 > num) {
                    num = pos + 1;
                }
            }
        }

        // クライアント側で同一IDの既存編成がある場合は、コンストラクタで上書きされる前に退避しておく
        Formation previousFormation = null;
        boolean isClientPacket = false;
        if (withEntries) {
            try {
                if (!NGTUtil.isServer()) {
                    isClientPacket = true;
                    FormationManager fm = FormationManager.getInstance();
                    if (fm != null) {
                        previousFormation = fm.getFormation(fid);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // サーバー側でまだ車両が1両もロードされていない空の PacketFormation を受け取った場合、
        // クライアント側に既に同じIDかつ同サイズ以上の有効な編成があればそれを維持して破壊を防ぐ
        if (isClientPacket && tagList != null && tagList.tagCount() == 0
                && previousFormation != null && previousFormation.entries != null
                && previousFormation.entries.length >= num) {
            boolean hasAnyResolved = false;
            for (FormationEntry existing : previousFormation.entries) {
                if (existing != null && existing.train != null && !existing.train.isDead) {
                    hasAnyResolved = true;
                    break;
                }
            }
            if (hasAnyResolved) {
                ((FormationBoundaryMixin) (Object) previousFormation).direction = nbt.getByte("Direction");
                cir.setReturnValue(previousFormation);
                return;
            }
        }

        Formation formation = new Formation(fid, num);
        ((FormationBoundaryMixin) (Object) formation).direction = nbt.getByte("Direction");

        if (withEntries && tagList != null) {
            List<int[]> pending = new ArrayList<>();
            World world = null;
            try {
                if (!NGTUtil.isServer()) {
                    world = NGTUtil.getClientWorld();
                }
            } catch (Throwable ignored) {
            }

            int[] receivedTrainIds = new int[Math.max(num, formation.entries.length)];
            Arrays.fill(receivedTrainIds, Integer.MIN_VALUE);

            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);
                int trainId = tag.getInteger("TrainId");
                int pos = tag.getByte("EntryPos");
                byte dir = tag.getByte("EntryDir");
                if (pos < 0) {
                    continue;
                }
                if (pos >= formation.entries.length) {
                    formation.entries = Arrays.copyOf(formation.entries, pos + 1);
                }
                if (pos >= receivedTrainIds.length) {
                    int oldLen = receivedTrainIds.length;
                    receivedTrainIds = Arrays.copyOf(receivedTrainIds, pos + 1);
                    Arrays.fill(receivedTrainIds, oldLen, receivedTrainIds.length, Integer.MIN_VALUE);
                }
                receivedTrainIds[pos] = trainId;

                EntityTrainBase train = null;
                if (world != null) {
                    try {
                        Entity entity = world.getEntityByID(trainId);
                        if (entity instanceof EntityTrainBase && !entity.isDead) {
                            train = (EntityTrainBase) entity;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (train != null) {
                    FormationEntry entry = new FormationEntry(train, pos, dir);
                    formation.entries[pos] = entry;
                    train.setFormation(formation);
                    FormationSyncHelper.recordFormationEntry(fid, trainId, pos, dir, true);
                } else {
                    pending.add(new int[] { trainId, pos, dir });
                    FormationSyncHelper.recordFormationEntry(fid, trainId, pos, dir, false);
                }
            }

            // サーバー側チャンク段階ロード中など、今回のパケットに一部スロットしか含まれていなかった場合、
            // 同一編成の旧インスタンスで既に解決済みだった未受信スロットの車両参照を引き継ぐ
            if (isClientPacket && previousFormation != null && previousFormation.entries != null
                    && tagList.tagCount() < formation.entries.length) {
                int limit = Math.min(previousFormation.entries.length, formation.entries.length);
                for (int i = 0; i < limit; i++) {
                    if (i < receivedTrainIds.length && receivedTrainIds[i] != Integer.MIN_VALUE) {
                        continue;
                    }
                    FormationEntry oldEntry = previousFormation.entries[i];
                    if (oldEntry != null && oldEntry.train != null && !oldEntry.train.isDead) {
                        // 既に別編成へ移動した車両でなければ引き継ぐ（Nettyスレッドから getFormation() を呼ばない）
                        long mappedFid = FormationSyncHelper.getMappedFormationId(oldEntry.train.getEntityId());
                        if (mappedFid == Long.MIN_VALUE || mappedFid == fid) {
                            FormationEntry carried = new FormationEntry(oldEntry.train, i, oldEntry.dir);
                            formation.entries[i] = carried;
                            oldEntry.train.setFormation(formation);
                        }
                    }
                }
            }

            if (isClientPacket) {
                FormationSyncHelper.pruneStaleEntriesForFormation(fid, formation.entries.length, receivedTrainIds);
                FormationSyncHelper.setPendingForFormation(fid, pending);
                FormationSyncHelper.notifyClientFormationUpdated();
            }
        }

        cir.setReturnValue(formation);
    }

    /**
     * getTrainStream が null 要素 (アンロード中などの車両) を含まないようにフィルタリングし、
     * isControlCar などのメソッド参照呼び出しによる NullPointerException を防止する。
     */
    @Inject(method = "getTrainStream", at = @At("RETURN"), cancellable = true, require = 0)
    private void crosstie$filterNullTrainsFromStream(CallbackInfoReturnable<Stream<EntityTrainBase>> cir) {
        Stream<EntityTrainBase> original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(original.filter(Objects::nonNull));
        }
    }

    /**
     * setEntry 呼び出し時にインデックスが配列長以上の場合は厳密なサイズ (par2 + 1) に自動拡張する。
     * 注意: Formation.size() は entries.length をそのまま返すため、2倍確保 (length * 2) を行うと
     * 末尾に余分な null スロットが生まれ、reverse() や connectTrain() 時に編成順序が破壊される。
     */
    @Inject(method = "setEntry", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$ensureCapacityOnSetEntry(FormationEntry entry, int par2, CallbackInfo ci) {
        if (par2 < 0) {
            ci.cancel();
            return;
        }

        if (this.entries == null) {
            this.entries = new FormationEntry[par2 + 1];
        } else if (par2 >= this.entries.length) {
            this.entries = Arrays.copyOf(this.entries, par2 + 1);
        }
    }

    /**
     * get 呼び出し時の境界チェックおよび自己修復 (Self-Healing)。
     */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$safeGet(int par1, CallbackInfoReturnable<FormationEntry> cir) {
        if (this.entries == null || par1 < 0 || par1 >= this.entries.length) {
            cir.setReturnValue(null);
            return;
        }

        FormationEntry entry = this.entries[par1];
        if (entry == null) {
            FormationEntry healed = FormationSyncHelper.tryHealEntry((Formation) (Object) this, par1);
            if (healed != null) {
                this.entries[par1] = healed;
                cir.setReturnValue(healed);
            }
        }
    }

    /**
     * getEntry 呼び出し時に未解決のエントリがあれば先に解決を試みる。
     * これにより描画スクリプトや getConnectedTrain(dir) での連結相手取得が確実に成功する。
     */
    @Inject(method = "getEntry", at = @At("HEAD"), require = 0)
    private void crosstie$healBeforeGetEntry(EntityTrainBase par1, CallbackInfoReturnable<FormationEntry> cir) {
        if (par1 == null || this.entries == null) {
            return;
        }
        if (par1.worldObj != null && par1.worldObj.isRemote) {
            for (int i = 0; i < this.entries.length; i++) {
                if (this.entries[i] == null) {
                    FormationSyncHelper.tryHealEntry((Formation) (Object) this, i);
                }
            }
        }
    }

    /**
     * isFrontCar 呼び出し時に entries が空または境界外の場合の ArrayIndexOutOfBoundsException を防止する。
     */
    @Inject(method = "isFrontCar", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$safeIsFrontCar(EntityTrainBase train, CallbackInfoReturnable<Boolean> cir) {
        if (train == null || this.entries == null || this.entries.length == 0) {
            cir.setReturnValue(false);
            return;
        }
        int frontIndex = (this.direction == 0) ? 0 : (this.entries.length - 1);
        FormationEntry fe = this.entries[frontIndex];
        if (fe != null && fe.train != null) {
            cir.setReturnValue(train.equals(fe.train));
            return;
        }
        // 先頭スロットがまだチャンク未ロード等で null の場合、ロード済みの最初の車両をフォールバック先頭車として扱う
        if (this.direction == 0) {
            for (int i = 0; i < this.entries.length; i++) {
                if (this.entries[i] != null && this.entries[i].train != null) {
                    cir.setReturnValue(train.equals(this.entries[i].train));
                    return;
                }
            }
        } else {
            for (int i = this.entries.length - 1; i >= 0; i--) {
                if (this.entries[i] != null && this.entries[i].train != null) {
                    cir.setReturnValue(train.equals(this.entries[i].train));
                    return;
                }
            }
        }
        cir.setReturnValue(false);
    }

    /**
     * reallocation 呼び出し時に entries 配列内に null ギャップ（過度拡張等で生じた空スロット）が
     * 含まれていた場合でも、有効なエントリだけを詰めて正確な両数・連番に正規化する。
     */
    @Inject(method = "reallocation", at = @At("HEAD"), require = 0)
    private void crosstie$compactNullsBeforeReallocation(CallbackInfo ci) {
        if (this.entries == null || this.entries.length == 0) {
            return;
        }
        int nonNullCount = 0;
        for (FormationEntry e : this.entries) {
            if (e != null && e.train != null && !e.train.isDead) {
                nonNullCount++;
            }
        }
        if (nonNullCount > 0 && nonNullCount < this.entries.length) {
            FormationEntry[] compacted = new FormationEntry[nonNullCount];
            int idx = 0;
            for (FormationEntry e : this.entries) {
                if (e != null && e.train != null && !e.train.isDead) {
                    compacted[idx++] = e;
                }
            }
            this.entries = compacted;
        }
    }

    /**
     * trim 呼び出し時の境界チェック。
     */
    @Inject(method = "trim", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$safeTrim(int start, int end, CallbackInfo ci) {
        if (this.entries == null || this.entries.length == 0 || start < 0 || end < start) {
            this.entries = new FormationEntry[0];
            ci.cancel();
            return;
        }

        int clampedEnd = Math.min(end, this.entries.length - 1);
        if (start > clampedEnd) {
            this.entries = new FormationEntry[0];
            ci.cancel();
            return;
        }

        int count = clampedEnd - start + 1;
        FormationEntry[] array = new FormationEntry[count];
        System.arraycopy(this.entries, start, array, 0, count);
        this.entries = array;
        ci.cancel();
    }
}
