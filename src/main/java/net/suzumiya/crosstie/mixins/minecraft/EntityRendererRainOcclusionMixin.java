package net.suzumiya.crosstie.mixins.minecraft;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.world.World;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererRainOcclusionMixin {

    @Redirect(
            method = {"renderRainSnow", "func_78474_d", "addRainParticles", "func_78484_h"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private int crosstie$redirectPrecipitationHeight(World world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }

    @Redirect(
            method = {"renderRainSnow", "func_78474_d", "addRainParticles", "func_78484_h"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private int crosstie$redirectPrecipitationHeightClient(net.minecraft.client.multiplayer.WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }
}
