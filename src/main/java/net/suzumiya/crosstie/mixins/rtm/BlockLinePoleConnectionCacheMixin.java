package net.suzumiya.crosstie.mixins.rtm;

import java.util.HashMap;
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

        Boolean cached = cache.values.get(crosstie$packKey(x, y, z, connectOther));
        if (cached != null) {
            cir.setReturnValue(cached);
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
            cache.values.put(crosstie$packKey(x, y, z, connectOther), cir.getReturnValue());
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
            cache.values.clear();
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

    @Unique
    private static final class TickCache {
        private long tick;
        private final Map<Long, Boolean> values = new HashMap<Long, Boolean>();
    }
}
