package net.suzumiya.crosstie.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highly optimized spatial index for tracking EntityTrainBase positions.
 * Replaces the extremely slow World#getEntitiesWithinAABB checks.
 */
public class TrainSpatialTracker {
    // DimensionID -> ChunkHash -> Sets of Trains
    private static final Map<Integer, Map<Long, Set<Entity>>> SPATIAL_GRID = new ConcurrentHashMap<>();
    
    // Train -> Its previously registered ChunkHashes
    private static final Map<Entity, Set<Long>> TRAIN_CACHED_CHUNKS = new ConcurrentHashMap<>();

    private static long getChunkHash(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public static void updateTrain(Entity train) {
        if (train.worldObj == null || train.worldObj.isRemote) return;
        
        if (train.isDead) {
            removeTrain(train);
            return;
        }

        int dim = train.worldObj.provider.dimensionId;
        Map<Long, Set<Entity>> dimGrid = SPATIAL_GRID.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        
        AxisAlignedBB bb = train.boundingBox;
        if (bb == null) return;
        
        int minX = (int) Math.floor(bb.minX) >> 4;
        int maxX = (int) Math.floor(bb.maxX) >> 4;
        int minZ = (int) Math.floor(bb.minZ) >> 4;
        int maxZ = (int) Math.floor(bb.maxZ) >> 4;

        Set<Long> currentChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                currentChunks.add(getChunkHash(x, z));
            }
        }
        
        Set<Long> previousChunks = TRAIN_CACHED_CHUNKS.get(train);
        
        // If chunks haven't changed, no need to update the grid
        if (previousChunks != null && previousChunks.equals(currentChunks)) {
            return;
        }
        
        // Remove from old chunks
        if (previousChunks != null) {
            for (Long hash : previousChunks) {
                if (!currentChunks.contains(hash)) {
                    Set<Entity> cell = dimGrid.get(hash);
                    if (cell != null) cell.remove(train);
                }
            }
        }
        
        // Add to new chunks
        for (Long hash : currentChunks) {
            if (previousChunks == null || !previousChunks.contains(hash)) {
                dimGrid.computeIfAbsent(hash, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(train);
            }
        }
        
        TRAIN_CACHED_CHUNKS.put(train, currentChunks);
    }
    
    public static void removeTrain(Entity train) {
        Set<Long> previousChunks = TRAIN_CACHED_CHUNKS.remove(train);
        if (previousChunks != null && train.worldObj != null) {
            int dim = train.worldObj.provider.dimensionId;
            Map<Long, Set<Entity>> dimGrid = SPATIAL_GRID.get(dim);
            if (dimGrid != null) {
                for (Long hash : previousChunks) {
                    Set<Entity> cell = dimGrid.get(hash);
                    if (cell != null) cell.remove(train);
                }
            }
        }
    }

    public static List<Entity> getTrainsWithinAABB(World world, AxisAlignedBB aabb) {
        List<Entity> result = new ArrayList<>();
        if (world == null || world.isRemote || aabb == null) return result;
        
        Map<Long, Set<Entity>> dimGrid = SPATIAL_GRID.get(world.provider.dimensionId);
        if (dimGrid == null) return result;
        
        int minX = (int) Math.floor(aabb.minX) >> 4;
        int maxX = (int) Math.floor(aabb.maxX) >> 4;
        int minZ = (int) Math.floor(aabb.minZ) >> 4;
        int maxZ = (int) Math.floor(aabb.maxZ) >> 4;
        
        // Keep track of visited trains to prevent duplicates if they span multiple chunks
        Set<Entity> visited = Collections.newSetFromMap(new ConcurrentHashMap<>());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Set<Entity> cell = dimGrid.get(getChunkHash(x, z));
                if (cell != null) {
                    for (Entity train : cell) {
                        if (train.isDead) continue;
                        if (visited.add(train) && train.boundingBox != null && train.boundingBox.intersectsWith(aabb)) {
                            result.add(train);
                        }
                    }
                }
            }
        }
        
        return result;
    }
}
