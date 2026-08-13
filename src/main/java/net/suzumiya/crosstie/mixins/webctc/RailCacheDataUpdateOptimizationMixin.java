package net.suzumiya.crosstie.mixins.webctc;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.suzumiya.crosstie.utils.TrackOccupancyTracker;
import org.webctc.cache.rail.RailCacheData;
import org.webctc.common.types.PosInt;
import org.webctc.common.types.rail.LargeRailData;

@Mixin(value = RailCacheData.class, remap = false)
public class RailCacheDataUpdateOptimizationMixin {

    // Disable forced chunk loading to completely eliminate lag
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/ChunkProviderServer;loadChunk(II)Lnet/minecraft/world/chunk/Chunk;"), remap = true)
    public Chunk onChunkProviderLoadChunk(ChunkProviderServer provider, int x, int z) {
        return null; // Do nothing
    }

    // Intercept putAll to apply our queued state changes before calculating diff
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/Map;putAll(Ljava/util/Map;)V"), remap = false)
    public void onPutAll(Map<PosInt, LargeRailData> coreList, Map<PosInt, LargeRailData> newLoadedData) {
        // Apply tracked state changes from RTM's real-time physics tick
        for (Map.Entry<PosInt, Boolean> pending : TrackOccupancyTracker.latestStates.entrySet()) {
            PosInt pos = pending.getKey();
            boolean actualOccupancy = pending.getValue();

            LargeRailData currentData = coreList.get(pos);
            if (currentData != null && currentData.isTrainOnRail() != actualOccupancy) {
                LargeRailData newData = new LargeRailData(
                        currentData.getPos(),
                        actualOccupancy,
                        currentData.getRailMaps(),
                        currentData.getTurning()
                );
                coreList.put(pos, newData);
            }
        }
        
        // Clear tracker after applying
        TrackOccupancyTracker.latestStates.clear();

        // Finally, do the original putAll for any newly loaded / updated tiles.
        // If a tile is currently loaded and ticked, its newLoadedData value will override our coreList value here, 
        // which is perfectly fine because newLoadedData represents the most up-to-date reality for loaded chunks.
        coreList.putAll(newLoadedData);
    }
}
