package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.world.World;
import net.suzumiya.crosstie.util.ChunkLoaderHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM TrainBase optimizations and a minimal chunk loader.
 *
 * <p>Uses @Shadow(remap = true) for Minecraft fields to ensure compatibility
 * with production environments while targeting a non-remapped mod class.
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseOptimizationMixin {

    @Unique
    private Object crosstie$chunkTicket;

    @Unique
    private int crosstie$lastChunkX = Integer.MIN_VALUE;

    @Unique
    private int crosstie$lastChunkZ = Integer.MIN_VALUE;

    @Unique
    private net.minecraft.entity.Entity crosstie$asEntity() {
        return (net.minecraft.entity.Entity) (Object) this;
    }

    /**
     * Minimal Chunk Loader: Ensures the current chunk is always forced on the server.
     * remap = false because we are targeting the mod's override of onUpdate.
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), remap = false)
    private void crosstie$manageChunkLoading(CallbackInfo ci) {
        net.minecraft.entity.Entity entity = this.crosstie$asEntity();
        if (entity.worldObj != null && !entity.worldObj.isRemote && !entity.isDead) {
            int curX = entity.chunkCoordX;
            int curZ = entity.chunkCoordZ;

            if (curX != this.crosstie$lastChunkX || curZ != this.crosstie$lastChunkZ || this.crosstie$chunkTicket == null) {
                this.crosstie$updateChunkTicket(curX, curZ);
            }
        }
    }

    @Unique
    private void crosstie$updateChunkTicket(int x, int z) {
        if (this.crosstie$chunkTicket == null) {
            // Helper handles the ticket request and entity binding
            this.crosstie$chunkTicket = ChunkLoaderHelper.requestTicket((net.minecraft.entity.Entity) (Object) this);
        }

        if (this.crosstie$chunkTicket != null) {
            if (this.crosstie$lastChunkX != Integer.MIN_VALUE) {
                ChunkLoaderHelper.unforceChunk(this.crosstie$chunkTicket, this.crosstie$lastChunkX, this.crosstie$lastChunkZ);
            }
            ChunkLoaderHelper.forceChunk(this.crosstie$chunkTicket, x, z);
            
            this.crosstie$lastChunkX = x;
            this.crosstie$lastChunkZ = z;
        }
    }

    /**
     * Release the ticket immediately when the entity is removed to prevent chunk leaks.
     * remap = false because we are targeting the mod's override of setDead.
     */
    @Inject(method = "setDead", at = @At("HEAD"), remap = false)
    private void crosstie$releaseTicketOnDeath(CallbackInfo ci) {
        if (this.crosstie$chunkTicket != null) {
            ChunkLoaderHelper.releaseTicket(this.crosstie$chunkTicket);
            this.crosstie$chunkTicket = null;
        }
    }
}
