package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.WireManager;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * S-3 / Phase 4-2: {@link WireManager#getWireY} のプリミティブ {@code long} マップ化＋空チャンクネガティブキャッシュ。
 *
 * <p>元の実装はルックアップごとに {@code new ChunkCoordIntPair(cX, cZ)} を生成し、
 * 以前の {@code HashMap<Long, ...>} でも毎回の {@code Long} ボクシングと空チャンク時のフォールバック生成が残っていた。
 * 自前のオープンアドレッシングプリミティブ {@code long -> List<WireEntry>} テーブルと空チャンクセンチネルにより、
 * ホットパスのアロケーションを完全にゼロにする。</p>
 */
@Mixin(value = WireManager.class, remap = false)
public abstract class WireManagerMixin {

    @Shadow
    private Map<ChunkCoordIntPair, List<WireManager.WireEntry>> loadedWires;

    @Unique
    private static final List<WireManager.WireEntry> CROSSTIE$EMPTY_SENTINEL = Collections.emptyList();

    @Unique
    private static final int CROSSTIE$INITIAL_CAPACITY = 64;

    @Unique
    private long[] crosstie$keys = new long[CROSSTIE$INITIAL_CAPACITY];

    @Unique
    @SuppressWarnings("unchecked")
    private List<WireManager.WireEntry>[] crosstie$values = new List[CROSSTIE$INITIAL_CAPACITY];

    @Unique
    private int crosstie$mask = CROSSTIE$INITIAL_CAPACITY - 1;

    @Unique
    private int crosstie$size = 0;

    @Unique
    private static long crosstie$toKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @Unique
    private static int crosstie$mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    @Unique
    private List<WireManager.WireEntry> crosstie$getCached(long key) {
        long[] keys = this.crosstie$keys;
        List<WireManager.WireEntry>[] values = this.crosstie$values;
        int mask = this.crosstie$mask;
        int idx = crosstie$mix(key) & mask;
        while (true) {
            List<WireManager.WireEntry> v = values[idx];
            if (v == null) {
                return null;
            }
            if (keys[idx] == key) {
                return v;
            }
            idx = (idx + 1) & mask;
        }
    }

    @Unique
    private void crosstie$putCached(long key, List<WireManager.WireEntry> val) {
        if ((this.crosstie$size + 1) * 4 >= this.crosstie$keys.length * 3) {
            this.crosstie$rehash();
        }
        long[] keys = this.crosstie$keys;
        List<WireManager.WireEntry>[] values = this.crosstie$values;
        int mask = this.crosstie$mask;
        int idx = crosstie$mix(key) & mask;
        while (true) {
            List<WireManager.WireEntry> v = values[idx];
            if (v == null) {
                keys[idx] = key;
                values[idx] = val;
                ++this.crosstie$size;
                return;
            }
            if (keys[idx] == key) {
                values[idx] = val;
                return;
            }
            idx = (idx + 1) & mask;
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void crosstie$rehash() {
        long[] oldKeys = this.crosstie$keys;
        List<WireManager.WireEntry>[] oldValues = this.crosstie$values;
        int newCap = oldKeys.length << 1;
        long[] newKeys = new long[newCap];
        List<WireManager.WireEntry>[] newValues = new List[newCap];
        int newMask = newCap - 1;
        for (int i = 0; i < oldValues.length; ++i) {
            List<WireManager.WireEntry> v = oldValues[i];
            if (v != null) {
                long k = oldKeys[i];
                int idx = crosstie$mix(k) & newMask;
                while (newValues[idx] != null) {
                    idx = (idx + 1) & newMask;
                }
                newKeys[idx] = k;
                newValues[idx] = v;
            }
        }
        this.crosstie$keys = newKeys;
        this.crosstie$values = newValues;
        this.crosstie$mask = newMask;
    }

    @Unique
    private void crosstie$clearCache() {
        if (this.crosstie$size > 0) {
            Arrays.fill(this.crosstie$values, null);
            this.crosstie$size = 0;
        }
    }

    /**
     * @author CrossTie
     * @reason プリミティブ long マップ＋空チャンクネガティブキャッシュで getWireY のアロケーションを完全ゼロ化する
     */
    @Overwrite(remap = false)
    public double getWireY(float yaw, double x, double y, double z) {
        if (this.loadedWires.isEmpty()) {
            return y;
        }

        int cX = MathHelper.floor_double(x) >> 4;
        int cZ = MathHelper.floor_double(z) >> 4;
        long key = crosstie$toKey(cX, cZ);

        List<WireManager.WireEntry> list = this.crosstie$getCached(key);
        if (list == null) {
            list = this.loadedWires.get(new ChunkCoordIntPair(cX, cZ));
            if (list == null || list.isEmpty()) {
                this.crosstie$putCached(key, CROSSTIE$EMPTY_SENTINEL);
                return y;
            }
            this.crosstie$putCached(key, list);
        } else if (list == CROSSTIE$EMPTY_SENTINEL) {
            return y;
        }

        double minY = y;
        boolean found = false;
        for (int i = 0, len = list.size(); i < len; ++i) {
            WireManager.WireEntry entry = list.get(i);
            if (entry.inRange(yaw, x, y, z)) {
                int index = entry.lineXZ.getNearlestPoint(512, x, z);
                double wireY = entry.minY + (entry.maxY - entry.minY) * ((double) index / 512.0D)
                        + jp.ngt.rtm.entity.train.EntityTrainBase.TRAIN_HEIGHT;
                if (!found || wireY < minY) {
                    minY = wireY;
                    found = true;
                }
            }
        }
        return found ? minY : y;
    }

    @Inject(method = "addWire", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$onAddWire(
            jp.ngt.rtm.electric.TileEntityElectricalWiring tileEntity,
            jp.ngt.rtm.electric.Connection connection,
            CallbackInfo ci) {
        this.crosstie$clearCache();
    }

    @Inject(method = "removeWire", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$onRemoveWire(
            jp.ngt.rtm.electric.TileEntityElectricalWiring tileEntity,
            jp.ngt.rtm.electric.Connection connection,
            CallbackInfo ci) {
        this.crosstie$clearCache();
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$onClear(CallbackInfo ci) {
        this.crosstie$clearCache();
    }
}
