package net.suzumiya.crosstie.mixins.minecraft;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allows server validation to account for an RTM vehicle moving between click
 * and packet handling.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class TrainEntityInteractionRangeMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "processUseEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayerMP;getDistanceSqToEntity(Lnet/minecraft/entity/Entity;)D"), require = 0)
    private double crosstie$allowMovingTrainInteraction(net.minecraft.entity.player.EntityPlayerMP player,
            Entity target) {
        double distanceSq = player.getDistanceSqToEntity(target);
        EntityVehicleBase vehicle = null;
        if (target instanceof EntityVehiclePart) {
            vehicle = ((EntityVehiclePart) target).getVehicle();
        } else if (target instanceof EntityBogie) {
            vehicle = ((EntityBogie) target).getTrain();
        }
        if (vehicle == null) {
            return distanceSq;
        }

        // Vanilla validates against 6 blocks. Extend the radius by at most 8 blocks,
        // covering up to four ticks of movement at the vehicle's current speed.
        double radius = 6.0D + Math.min(8.0D, Math.abs(vehicle.getSpeed()) * 4.0D);
        return distanceSq <= radius * radius ? 0.0D : distanceSq;
    }
}
