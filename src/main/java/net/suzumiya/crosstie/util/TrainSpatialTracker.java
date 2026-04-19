package net.suzumiya.crosstie.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Highly optimized spatial index for tracking EntityTrainBase positions.
 * Replaces the extremely slow World#getEntitiesWithinAABB checks.
 */
public class TrainSpatialTracker {
    // DimensionID -> ChunkHash -> Sets of Trains
    private static final ConcurrentMap<Integer, ConcurrentMap<Long, Set<Entity>>> SPATIAL_GRID =
            new ConcurrentHashMap<Integer, ConcurrentMap<Long, Set<Entity>>>();
    // Train -> last chunk span occupied by the train AABB
    private static final Map<Entity, ChunkSpan> TRAIN_CHUNK_SPANS = new ConcurrentHashMap<Entity, ChunkSpan>();
    private static final int DEFAULT_CELL_SET_CAPACITY = 4;

    private static long getChunkHash(int x, int z) {
        return ((long) x & 4294967295L) | (((long) z & 4294967295L) << 32);
    }

    public static void updateTrain(Entity train) {
        if (train.worldObj == null || train.worldObj.isRemote) return;

        if (train.isDead) {
            removeTrain(train);
            return;
        }

        int dim = train.worldObj.provider.dimensionId;
        ConcurrentMap<Long, Set<Entity>> dimGrid = SPATIAL_GRID.get(Integer.valueOf(dim));
        if (dimGrid == null) {
            dimGrid = new ConcurrentHashMap<Long, Set<Entity>>();
            ConcurrentMap<Long, Set<Entity>> previous = SPATIAL_GRID.putIfAbsent(Integer.valueOf(dim), dimGrid);
            if (previous != null) {
                dimGrid = previous;
            }
        }

        AxisAlignedBB bb = train.boundingBox;
        if (bb == null) return;

        int minX = (int) Math.floor(bb.minX) >> 4;
        int maxX = (int) Math.floor(bb.maxX) >> 4;
        int minZ = (int) Math.floor(bb.minZ) >> 4;
        int maxZ = (int) Math.floor(bb.maxZ) >> 4;

        ChunkSpan previous = TRAIN_CHUNK_SPANS.get(train);
        if (previous != null && previous.matches(minX, maxX, minZ, maxZ)) {
            return;
        }

        if (previous != null) {
            for (int x = previous.minX; x <= previous.maxX; x++) {
                for (int z = previous.minZ; z <= previous.maxZ; z++) {
                    if (x < minX || x > maxX || z < minZ || z > maxZ) {
                        removeTrainFromCell(dimGrid, getChunkHash(x, z), train);
                    }
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (previous == null || x < previous.minX || x > previous.maxX || z < previous.minZ || z > previous.maxZ) {
                    addTrainToCell(dimGrid, getChunkHash(x, z), train);
                }
            }
        }

        if (previous == null) {
            TRAIN_CHUNK_SPANS.put(train, new ChunkSpan(minX, maxX, minZ, maxZ));
        } else {
            previous.set(minX, maxX, minZ, maxZ);
        }
    }

    public static void removeTrain(Entity train) {
        ChunkSpan previous = TRAIN_CHUNK_SPANS.remove(train);
        if (previous != null && train.worldObj != null) {
            int dim = train.worldObj.provider.dimensionId;
            ConcurrentMap<Long, Set<Entity>> dimGrid = SPATIAL_GRID.get(Integer.valueOf(dim));
            if (dimGrid != null) {
                for (int x = previous.minX; x <= previous.maxX; x++) {
                    for (int z = previous.minZ; z <= previous.maxZ; z++) {
                        removeTrainFromCell(dimGrid, getChunkHash(x, z), train);
                    }
                }
            }
        }
    }

    public static List<Entity> getTrainsWithinAABB(World world, AxisAlignedBB aabb) {
        List<Entity> result = new ArrayList<Entity>();
        Set<Entity> visited = java.util.Collections.newSetFromMap(new ConcurrentHashMap<Entity, Boolean>());
        fillTrainsWithinAABB(world, aabb, result, visited);
        return result;
    }

    public static void fillTrainsWithinAABB(World world, AxisAlignedBB aabb, List<Entity> out, Set<Entity> visited) {
        out.clear();
        visited.clear();
        if (world == null || world.isRemote || aabb == null) return;
        
        ConcurrentMap<Long, Set<Entity>> dimGrid = SPATIAL_GRID.get(Integer.valueOf(world.provider.dimensionId));
        if (dimGrid == null) return;
        
        int minX = (int) Math.floor(aabb.minX) >> 4;
        int maxX = (int) Math.floor(aabb.maxX) >> 4;
        int minZ = (int) Math.floor(aabb.minZ) >> 4;
        int maxZ = (int) Math.floor(aabb.maxZ) >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Set<Entity> cell = dimGrid.get(getChunkHash(x, z));
                if (cell != null) {
                    for (Entity train : cell) {
                        if (train.isDead) continue;
                        if (visited.add(train) && train.boundingBox != null && train.boundingBox.intersectsWith(aabb)) {
                            out.add(train);
                        }
                    }
                }
            }
        }
    }

    private static void addTrainToCell(ConcurrentMap<Long, Set<Entity>> dimGrid, long chunkHash, Entity train) {
        Long key = Long.valueOf(chunkHash);
        Set<Entity> cell = dimGrid.get(key);
        if (cell == null) {
            Set<Entity> created = java.util.Collections.newSetFromMap(new ConcurrentHashMap<Entity, Boolean>(DEFAULT_CELL_SET_CAPACITY));
            Set<Entity> previous = dimGrid.putIfAbsent(key, created);
            cell = previous != null ? previous : created;
        }
        cell.add(train);
    }

    private static void removeTrainFromCell(ConcurrentMap<Long, Set<Entity>> dimGrid, long chunkHash, Entity train) {
        Long key = Long.valueOf(chunkHash);
        Set<Entity> cell = dimGrid.get(key);
        if (cell != null) {
            cell.remove(train);
            if (cell.isEmpty()) {
                dimGrid.remove(key, cell);
            }
        }
    }

    private static final class ChunkSpan {
        private int minX;
        private int maxX;
        private int minZ;
        private int maxZ;

        private ChunkSpan(int minX, int maxX, int minZ, int maxZ) {
            this.set(minX, maxX, minZ, maxZ);
        }

        private boolean matches(int minX, int maxX, int minZ, int maxZ) {
            return this.minX == minX && this.maxX == maxX && this.minZ == minZ && this.maxZ == maxZ;
        }

        private void set(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
