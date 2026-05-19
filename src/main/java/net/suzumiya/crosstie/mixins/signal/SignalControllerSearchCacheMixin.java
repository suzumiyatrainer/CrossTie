package net.suzumiya.crosstie.mixins.signal;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caches SignalControllerMod's upward signal search.
 *
 * <p>The target method is {@code searchSignalAboveY(World): int}. The cache only stores
 * positive hits and verifies the cached TileEntity before returning it, so newly placed
 * signals are still detected by the normal scan path.
 */
@Mixin(targets = "jp.masa.signalcontrollermod.block.tileentity.TileEntitySignalController", remap = false)
public abstract class SignalControllerSearchCacheMixin {

    @Unique
    private TileEntity crosstie$asTile() {
        return (TileEntity) (Object) this;
    }

    @Unique
    private static final long CACHE_LIFETIME_TICKS = 40L;

    @Unique
    private final Map<Long, WeakReference<TileEntity>> crosstie$searchCache = new HashMap<>();

    @Unique
    private long crosstie$nextCacheReset = 0L;

    @Inject(
            method = "searchSignalAboveY",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void crosstie$tryReturnCachedSignalAbove(
            World world,
            CallbackInfoReturnable<Integer> cir) {
        long tick = world.getTotalWorldTime();
        if (tick >= this.crosstie$nextCacheReset) {
            this.crosstie$searchCache.clear();
            this.crosstie$nextCacheReset = tick + CACHE_LIFETIME_TICKS;
            return;
        }

        TileEntity tile = this.crosstie$asTile();
        long key = crosstie$packSearchKey(tile.xCoord, tile.yCoord, tile.zCoord);
        WeakReference<TileEntity> ref = this.crosstie$searchCache.get(key);
        if (ref == null) {
            return;
        }

        TileEntity cached = ref.get();
        if (cached == null || cached.isInvalid() || !crosstie$isSignalTile(cached)) {
            this.crosstie$searchCache.remove(key);
            return;
        }

        cir.setReturnValue(cached.yCoord);
    }

    @Inject(
            method = "searchSignalAboveY",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private void crosstie$cacheSearchResult(
            World world,
            CallbackInfoReturnable<Integer> cir) {
        int y = cir.getReturnValue();
        if (y <= 0) {
            return;
        }

        TileEntity tile = this.crosstie$asTile();
        TileEntity tileEntity = world.getTileEntity(tile.xCoord, y, tile.zCoord);
        if (tileEntity != null && crosstie$isSignalTile(tileEntity)) {
            long key = crosstie$packSearchKey(tile.xCoord, tile.yCoord, tile.zCoord);
            this.crosstie$searchCache.put(key, new WeakReference<>(tileEntity));
        }
    }

    @Unique
    private static boolean crosstie$isSignalTile(TileEntity tileEntity) {
        return "jp.ngt.rtm.electric.TileEntitySignal".equals(tileEntity.getClass().getName());
    }

    @Unique
    private static long crosstie$packSearchKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 36)
                | ((long) (y & 0xFF) << 28)
                | ((long) (z & 0x3FFFFF) << 6);
    }
}
