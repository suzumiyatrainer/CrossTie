package net.suzumiya.crosstie.util;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.suzumiya.crosstie.CrossTie;

/**
 * Helper class to decouple Mixins from Forge Chunk Manager classes.
 * This prevents NoClassDefFoundErrors during early Mixin transformation.
 */
public class ChunkLoaderHelper {

    /**
     * Requests a chunk ticket for the given entity.
     * Uses Object for the return type to avoid Forge class dependencies in calling Mixins.
     */
    public static Object requestTicket(Entity entity) {
        if (entity == null || entity.worldObj == null) return null;
        
        try {
            World world = entity.worldObj;
            Ticket ticket = ForgeChunkManager.requestTicket(CrossTie.instance, world, Type.ENTITY);
            if (ticket != null) {
                ticket.bindEntity(entity);
            }
            return ticket;
        } catch (Throwable e) {
            // Log once to avoid spamming if Forge is not ready
            return null;
        }
    }

    public static void forceChunk(Object ticketObj, int x, int z) {
        if (ticketObj instanceof Ticket) {
            try {
                ForgeChunkManager.forceChunk((Ticket) ticketObj, new ChunkCoordIntPair(x, z));
            } catch (Throwable ignored) {}
        }
    }

    public static void unforceChunk(Object ticketObj, int x, int z) {
        if (ticketObj instanceof Ticket) {
            try {
                ForgeChunkManager.unforceChunk((Ticket) ticketObj, new ChunkCoordIntPair(x, z));
            } catch (Throwable ignored) {}
        }
    }

    public static void releaseTicket(Object ticketObj) {
        if (ticketObj instanceof Ticket) {
            try {
                ForgeChunkManager.releaseTicket((Ticket) ticketObj);
            } catch (Throwable ignored) {}
        }
    }
}
