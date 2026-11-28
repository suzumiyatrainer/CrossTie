package net.suzumiya.crosstie.mixins.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.cache.VehicleUUIDIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link World#onEntityAdded(Entity)} / {@link World#onEntityRemoved(Entity)} をフックし、
 * {@link VehicleUUIDIndex} を最新の車両エンティティ集合と同期する。
 */
@Mixin(World.class)
public abstract class WorldVehicleIndexMixin {

    @Inject(method = "onEntityAdded", at = @At("TAIL"))
    private void crosstie$onEntityAdded(Entity entity, CallbackInfo ci) {
        VehicleUUIDIndex.onEntityAdded((World) (Object) this, entity);
    }

    @Inject(method = "onEntityRemoved", at = @At("TAIL"))
    private void crosstie$onEntityRemoved(Entity entity, CallbackInfo ci) {
        VehicleUUIDIndex.onEntityRemoved((World) (Object) this, entity);
    }
}
