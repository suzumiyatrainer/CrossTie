package net.suzumiya.crosstie.mixins.bamboo;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "ruby.bamboo.tileentity.TileEntityCampfire",
        "ruby.bamboo.tileentity.TileEntityAndon",
        "ruby.bamboo.tileentity.TileEntityManeki",
        "ruby.bamboo.tileentity.TileEntityMillStone",
        "ruby.bamboo.tileentity.TileEntityMultiPot",
        "ruby.bamboo.tileentity.TileEntitySpaParent",
        "ruby.bamboo.tileentity.TileEntityMultiBlock",
        "ruby.bamboo.tileentity.TileEntityVillagerBlock"
}, remap = false)
public abstract class BambooTileEntityMixin extends TileEntity {

    @Unique
    private int crosstie$updatePhase = Integer.MIN_VALUE;

    @Inject(method = { "updateEntity", "func_145845_h" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullUpdate(CallbackInfo ci) {
        final World world = this.worldObj;
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            final int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0) {
                return;
            }

            final double cullLimit = renderChunks * 16.0D;
            final double limitSq = cullLimit * cullLimit;
            final Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && this.crosstie$distanceSqTo(player.posX, player.posY, player.posZ) > limitSq) {
                ci.cancel();
            }
            return;
        }

        if (this.crosstie$updatePhase == Integer.MIN_VALUE) {
            this.crosstie$updatePhase = this.hashCode() & 19;
        }

        final long worldTime = world.getTotalWorldTime();
        if ((worldTime + this.crosstie$updatePhase) % 20L == 0L) {
            final double nearLimitSq = 64.0D * 64.0D;
            final java.util.List players = world.playerEntities;
            boolean isPlayerNear = false;
            for (int i = 0, size = players.size(); i < size; i++) {
                final Object obj = players.get(i);
                if (obj instanceof Entity) {
                    final Entity p = (Entity) obj;
                    if (this.crosstie$distanceSqTo(p.posX, p.posY, p.posZ) < nearLimitSq) {
                        isPlayerNear = true;
                        break;
                    }
                }
            }
            if (!isPlayerNear) {
                ci.cancel();
            }
            return;
        }

        if ((worldTime & 1L) != 0L) {
            ci.cancel();
        }
    }

    @Unique
    private double crosstie$distanceSqTo(double x, double y, double z) {
        final double dx = this.xCoord + 0.5D - x;
        final double dy = this.yCoord + 0.5D - y;
        final double dz = this.zCoord + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
