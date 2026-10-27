package net.suzumiya.crosstie.mixins.rtm;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shares line-pole adjacency queries within the same client tick.
 *
 * <p>RenderConnectablePole.js asks BlockLinePole.isConnected six times per pole render.
 * The result depends only on the queried world/block state, so a one-tick client cache
 * avoids repeating the same world.getBlock lookups across dense pole clusters.
 *
 * <p>このキャッシュはクライアントスレッドのみで使用されるため、
 * synchronized ラッパーは不要。WeakHashMap で十分。
 * また、GTNHLib/fastutil 非導入環境でも動作するよう自前の軽量プリミティブマップを使用する。
 */
@Mixin(targets = "jp.ngt.rtm.block.BlockLinePole", remap = false)
public abstract class BlockLinePoleConnectionCacheMixin {

    @Unique
    private static final Map<World, TickCache> crosstie$connectionCacheByWorld =
            new WeakHashMap<World, TickCache>();


    @Inject(method = "isConnected", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$getCachedConnection(
            IBlockAccess world,
            int x,
            int y,
            int z,
            boolean connectOther,
            CallbackInfoReturnable<Boolean> cir) {
        if (!CrossTieConfig.connectionCacheEnabled) {
            return;
        }
        TickCache cache = crosstie$getClientTickCache(world);
        if (cache == null) {
            return;
        }

        byte cached = cache.get(crosstie$packKey(x, y, z, connectOther));
        if (cached != -1) {
            cir.setReturnValue(cached == 1);
        }
    }

    @Inject(method = "isConnected", at = @At("RETURN"), require = 0, remap = false)
    private static void crosstie$cacheConnection(
            IBlockAccess world,
            int x,
            int y,
            int z,
            boolean connectOther,
            CallbackInfoReturnable<Boolean> cir) {
        TickCache cache = crosstie$getClientTickCache(world);
        if (cache != null) {
            cache.put(crosstie$packKey(x, y, z, connectOther), cir.getReturnValue() ? (byte) 1 : (byte) 0);
        }
    }

    @Unique
    private static TickCache crosstie$getClientTickCache(IBlockAccess world) {
        if (!(world instanceof World)) {
            return null;
        }

        World mcWorld = (World) world;
        if (!mcWorld.isRemote) {
            return null;
        }

        TickCache cache = crosstie$connectionCacheByWorld.get(mcWorld);
        if (cache == null) {
            cache = new TickCache();
            cache.tick = mcWorld.getTotalWorldTime();
            crosstie$connectionCacheByWorld.put(mcWorld, cache);
            return cache;
        }

        long tick = mcWorld.getTotalWorldTime();
        if (cache.tick != tick) {
            cache.tick = tick;
            cache.clear();
        }
        return cache;
    }

    @Unique
    private static long crosstie$packKey(int x, int y, int z, boolean connectOther) {
        long key = (((long) x & 0x3FFFFFFL) << 38)
                | (((long) y & 0xFFFL) << 26)
                | ((long) z & 0x3FFFFFFL);
        return connectOther ? key ^ Long.MIN_VALUE : key;
    }

    /**
     * Lightweight open-addressing long-to-byte cache without fastutil dependency.
     * Values stored in {@code states}: 0 = empty, 1 = false (cached 0), 2 = true (cached 1).
     */
    @Unique
    private static final class TickCache {
        private long tick;
        private long[] keys = new long[256];
        private byte[] states = new byte[256];
        private int mask = 255;
        private int size = 0;
        private int maxFill = 192;

        byte get(long key) {
            if (size == 0) {
                return -1;
            }
            int idx = mix64(key) & mask;
            while (true) {
                byte st = states[idx];
                if (st == 0) {
                    return -1;
                }
                if (keys[idx] == key) {
                    return (byte) (st - 1);
                }
                idx = (idx + 1) & mask;
            }
        }

        void put(long key, byte val) {
            byte stVal = (byte) (val + 1);
            int idx = mix64(key) & mask;
            while (true) {
                byte st = states[idx];
                if (st == 0) {
                    keys[idx] = key;
                    states[idx] = stVal;
                    if (++size >= maxFill) {
                        rehash(keys.length << 1);
                    }
                    return;
                }
                if (keys[idx] == key) {
                    states[idx] = stVal;
                    return;
                }
                idx = (idx + 1) & mask;
            }
        }

        void clear() {
            if (size != 0) {
                Arrays.fill(states, (byte) 0);
                size = 0;
            }
        }

        private void rehash(int newCap) {
            long[] oldKeys = this.keys;
            byte[] oldStates = this.states;

            this.keys = new long[newCap];
            this.states = new byte[newCap];
            this.mask = newCap - 1;
            this.maxFill = (newCap * 3) >>> 2;

            for (int i = 0; i < oldStates.length; i++) {
                byte st = oldStates[i];
                if (st != 0) {
                    long k = oldKeys[i];
                    int idx = mix64(k) & mask;
                    while (states[idx] != 0) {
                        idx = (idx + 1) & mask;
                    }
                    keys[idx] = k;
                    states[idx] = st;
                }
            }
        }

        private static int mix64(long z) {
            z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
            z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return (int) (z ^ (z >>> 33));
        }
    }
}
