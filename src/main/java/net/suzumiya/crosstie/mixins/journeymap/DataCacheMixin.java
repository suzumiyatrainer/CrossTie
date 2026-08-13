package net.suzumiya.crosstie.mixins.journeymap;

import journeymap.client.data.DataCache;
import journeymap.client.model.chunk.ChunkMD;
import journeymap.common.Journeymap;
import journeymap.common.helper.DimensionHelper;
import journeymap.client.io.nbt.JMChunkLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.ChunkCoordIntPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.google.common.cache.Cache;

@Mixin(value = DataCache.class, remap = false)
public abstract class DataCacheMixin {

    @Shadow
    final Cache<Long, ChunkMD> chunkMetadata = null;

    /**
     * @author CrossTie
     * @reason Remove synchronized block around Guava Cache to fix massive lag spikes when Angelica concurrently loads chunks.
     */
    @Overwrite
    public ChunkMD getChunkMD(long coordLong, DataCache.ChunkQueryIntent intent) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.theWorld != mc.thePlayer.getEntityWorld()) {
            return null;
        }
        
        try {
            ChunkMD chunkMD = this.chunkMetadata.asMap().get(Long.valueOf(coordLong));
            if (intent == DataCache.ChunkQueryIntent.ALLOW_CACHED && chunkMD != null && chunkMD.hasChunk()) {
                return chunkMD;
            }
            if (chunkMD != null && chunkMD.hasChunk()) {
                if (intent == DataCache.ChunkQueryIntent.LIVE_ONLY) {
                    try {
                        if (chunkMD.getChunk().worldObj == mc.thePlayer.getEntityWorld()) {
                            return chunkMD;
                        }
                    } catch (Exception exception) {}
                } else {
                    return chunkMD;
                }
            }
            if ((chunkMD = JMChunkLoader.getChunkMdFromMemory(mc.thePlayer.getEntityWorld(), (int)coordLong, (int)(coordLong >> 32))) != null 
                    && chunkMD.hasChunk() 
                    && DimensionHelper.getDimension(chunkMD.getChunk().worldObj) == DimensionHelper.getDimension(mc.thePlayer.getEntityWorld())) {
                this.chunkMetadata.asMap().put(Long.valueOf(coordLong), chunkMD);
                return chunkMD;
            }
            if (chunkMD != null) {
                this.chunkMetadata.asMap().remove(Long.valueOf(coordLong));
            }
            return null;
        } catch (Throwable e) {
            Journeymap.getLogger().warn("Unexpected error getting ChunkMD from cache: ", e);
            return null;
        }
    }

    /**
     * @author CrossTie
     * @reason Remove synchronized block around Guava Cache
     */
    @Overwrite
    public void addChunkMD(ChunkMD chunkMD) {
        this.chunkMetadata.asMap().put(Long.valueOf(chunkMD.getLongCoord()), chunkMD);
    }

    /**
     * @author CrossTie
     * @reason Remove synchronized block around Guava Cache
     */
    @Overwrite
    public void removeChunkMD(ChunkMD chunkMD) {
        this.chunkMetadata.asMap().remove(Long.valueOf(chunkMD.getLongCoord()));
    }

    /**
     * @author CrossTie
     * @reason Remove synchronized block around Guava Cache
     */
    @Overwrite
    public void invalidateChunkMD(ChunkCoordIntPair coord) {
        long key = ChunkCoordIntPair.chunkXZ2Int(coord.chunkXPos, coord.chunkZPos);
        this.chunkMetadata.asMap().remove(Long.valueOf(key));
    }
}
