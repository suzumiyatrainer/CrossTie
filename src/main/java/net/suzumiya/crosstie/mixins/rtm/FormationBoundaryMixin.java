package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * {@link Formation} の配列境界外アクセス (ArrayIndexOutOfBoundsException) を防止し、
 * クライアント側での欠落エントリの自動自己修復 (Self-Healing) を行う Mixin。
 *
 * <p>
 * Issue #87 の修正:
 * <ol>
 * <li>{@code setEntry} でインデックスが配列長以上の場合は安全に自動拡張。</li>
 * <li>{@code get} でインデックス境界チェックを行い、null エントリがあればクライアント側エンティティから自動自己修復。</li>
 * <li>{@code trim} での負数インデックスや空配列のクラッシュ防止。</li>
 * </ol>
 */
@Mixin(value = Formation.class, remap = false)
public abstract class FormationBoundaryMixin {

    @Shadow
    public long id;

    @Shadow
    public FormationEntry[] entries;

    /**
     * PacketFormation 受信時にエンティティが未解決で null となったエントリ情報 (trainId, pos, dir)。
     * 編成ID単位で保持し、get() 時に再解決する。
     */
    @Unique
    private static final ConcurrentHashMap<Long, List<int[]>> crosstie$pending = new ConcurrentHashMap<>();

    /**
     * クライアントで Formation.readFromNBT した直後、未解決のエントリを記録する。
     */
    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 0)
    private static void crosstie$recordPending(NBTTagCompound nbt, boolean withEntries,
            CallbackInfoReturnable<Formation> cir) {
        Formation f = cir.getReturnValue();
        if (f == null || !withEntries || f.entries == null) {
            return;
        }
        List<int[]> pending = new ArrayList<>();
        NBTTagList tagList = nbt.getTagList("Entries", 10);
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            int pos = tag.getByte("EntryPos");
            if (pos >= 0 && pos < f.entries.length && f.entries[pos] == null) {
                pending.add(new int[] { tag.getInteger("TrainId"), pos, tag.getByte("EntryDir") });
            }
        }
        if (pending.isEmpty()) {
            crosstie$pending.remove(f.id);
        } else {
            crosstie$pending.put(f.id, pending);
        }
    }

    /**
     * getTrainStream が null 要素 (アンロード中などの車両) を含まないようにフィルタリングし、
     * isControlCar などのメソッド参照呼び出しによる NullPointerException を防止する。
     */
    @Inject(method = "getTrainStream", at = @At("RETURN"), cancellable = true, require = 0)
    private void crosstie$filterNullTrainsFromStream(CallbackInfoReturnable<java.util.stream.Stream<EntityTrainBase>> cir) {
        java.util.stream.Stream<EntityTrainBase> original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(original.filter(java.util.Objects::nonNull));
        }
    }

    /**
     * setEntry 呼び出し時にインデックスが配列長以上の場合は自動拡張する。
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
            int newLength = Math.max(par2 + 1, this.entries.length * 2);
            this.entries = Arrays.copyOf(this.entries, newLength);
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
            // クライアント側でパケット順序やタイミングによりエントリが null になっている場合、自己修復を試みる
            FormationEntry healed = crosstie$tryHealEntry(par1);
            if (healed != null) {
                this.entries[par1] = healed;
                cir.setReturnValue(healed);
            }
        }
    }

    /**
     * クライアントの世界にロードされている車両から、この Formation に属すべき車両を探してエントリを再構築する。
     */
    @Unique
    private FormationEntry crosstie$tryHealEntry(int targetPos) {
        try {
            if (NGTUtil.isServer()) {
                return null;
            }

            World world = NGTUtil.getClientWorld();
            if (world == null || world.loadedEntityList == null) {
                return null;
            }

            // 1) パケットで受信済みだが当時エンティティが未解決だったエントリを再解決
            List<int[]> pending = crosstie$pending.get(this.id);
            if (pending != null) {
                synchronized (pending) {
                    for (java.util.Iterator<int[]> it = pending.iterator(); it.hasNext();) {
                        int[] p = it.next();
                        if (p[1] != targetPos) {
                            continue;
                        }
                        Entity e = world.getEntityByID(p[0]);
                        if (e instanceof EntityTrainBase) {
                            EntityTrainBase t = (EntityTrainBase) e;
                            it.remove();
                            t.setFormation((Formation) (Object) this);
                            return new FormationEntry(t, p[1], p[2]);
                        }
                    }
                }
            }

            @SuppressWarnings("unused")
            Formation self = (Formation) (Object) this;
            List<?> list = world.loadedEntityList;
            for (Object obj : list) {
                if (obj instanceof EntityTrainBase) {
                    EntityTrainBase train = (EntityTrainBase) obj;
                    Formation trainFormation = train.getFormation();
                    if (trainFormation != null && trainFormation.id == this.id) {
                        FormationEntry existing = trainFormation.getEntry(train);
                        if (existing != null && existing.entryId == targetPos) {
                            return existing;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
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
