package net.suzumiya.crosstie.mixins.webctc;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.suzumiya.crosstie.util.ChunkLoadRateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.webctc.cache.rail.RailCacheData", remap = false)
public abstract class RailCacheDataMixin {

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunkProvider;loadChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk crosstie$rateLimitForcedRailChunkLoads(IChunkProvider provider, int chunkX, int chunkZ) {
        return ChunkLoadRateLimiter.loadWebCTCRailChunk(provider, chunkX, chunkZ);
    }
}

