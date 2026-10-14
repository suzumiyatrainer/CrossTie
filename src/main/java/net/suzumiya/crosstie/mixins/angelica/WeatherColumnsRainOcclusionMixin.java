package net.suzumiya.crosstie.mixins.angelica;

import net.minecraft.world.World;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.gtnewhorizons.angelica.render.WeatherColumns", remap = false)
public abstract class WeatherColumnsRainOcclusionMixin {

    @Redirect(
            method = "sample",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private int crosstie$redirectAngelicaWorldPrecipitationHeight(World world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }

    @Redirect(
            method = "sample",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private int crosstie$redirectAngelicaClientPrecipitationHeight(net.minecraft.client.multiplayer.WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }
}
