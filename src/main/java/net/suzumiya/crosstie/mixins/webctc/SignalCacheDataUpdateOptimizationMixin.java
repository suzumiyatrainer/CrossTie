package net.suzumiya.crosstie.mixins.webctc;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.suzumiya.crosstie.utils.WebCtcDirtyTracker;
import org.webctc.common.types.PosInt;
import org.webctc.common.types.signal.SignalData;

@Mixin(targets = "org.webctc.cache.signal.SignalCacheData", remap = false)
public abstract class SignalCacheDataUpdateOptimizationMixin {

    /**
     * WebCTC 1.2.0: update() 冒頭の
     * signalMapCache.filter { !world.getChunkFromBlockCoords(...).isChunkLoaded || ... }
     * において、isChunkLoaded の読み出しを常に false に偽装し、全信号機を coreList に保持する。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/chunk/Chunk;field_76636_d:Z",
                    remap = true
            ),
            remap = false
    )
    private boolean crosstie$keepAllCachedSignalsInCoreList(Chunk chunk) {
        return false;
    }

    /**
     * WebCTC 1.2.0: SignalCacheData.update() 内の world.loadedTileEntityList へのアクセスをインターセプトし、
     * 初回は全件、2回目以降は状態変化（現示変化など）があった Dirty な TileEntitySignal のみを提供する。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/World;field_147482_g:Ljava/util/List;",
                    remap = true
            ),
            remap = false
    )
    private List<?> crosstie$redirectLoadedTileEntityList(World world) {
        return WebCtcDirtyTracker.pollDirtySignalsOrAll(world.loadedTileEntityList);
    }

    /**
     * WebCTC 1.2.0: coreList.putAll(dirtyData) をインターセプトし、
     * 破壊された信号機を coreList から削除した上で、変更があった Dirty 信号機を反映する。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;putAll(Ljava/util/Map;)V"
            ),
            remap = false
    )
    private void crosstie$onCoreListPutAll(Map<PosInt, SignalData> coreList, Map<PosInt, SignalData> dirtyData) {
        coreList.putAll(dirtyData);
        List<Long> removed = WebCtcDirtyTracker.pollRemovedSignals();
        if (!removed.isEmpty()) {
            for (Long packed : removed) {
                int x = WebCtcDirtyTracker.unpackX(packed);
                int y = WebCtcDirtyTracker.unpackY(packed);
                int z = WebCtcDirtyTracker.unpackZ(packed);
                coreList.remove(new PosInt(x, y, z));
            }
        }
    }
}
