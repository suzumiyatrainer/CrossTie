package net.suzumiya.crosstie.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.suzumiya.crosstie.config.CrossTieConfig;

public final class ChunkLoadRateLimiter {

    private static int lastTick = Integer.MIN_VALUE;
    private static int loadsThisTick;

    private ChunkLoadRateLimiter() {
    }

    public static Chunk loadWebCTCRailChunk(IChunkProvider provider, int chunkX, int chunkZ) {
        if (provider == null) {
            return null;
        }
        if (!CrossTieConfig.enableWebCTCChunkLoadRateLimit) {
            return provider.loadChunk(chunkX, chunkZ);
        }
        int maxLoads = Math.max(0, CrossTieConfig.webCTCChunkLoadsPerTick);
        if (maxLoads == 0) {
            return null;
        }
        int tick = getServerTick();
        synchronized (ChunkLoadRateLimiter.class) {
            if (tick != lastTick) {
                lastTick = tick;
                loadsThisTick = 0;
            }
            if (loadsThisTick >= maxLoads) {
                return null;
            }
            loadsThisTick++;
        }
        return provider.loadChunk(chunkX, chunkZ);
    }

    private static int getServerTick() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? 0 : server.getTickCounter();
    }
}
