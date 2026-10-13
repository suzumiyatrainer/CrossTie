package net.suzumiya.crosstie.mixins.angelica;

import net.minecraft.client.multiplayer.WorldClient;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.gtnewhorizons.angelica.render.WeatherRenderer", remap = false)
public abstract class WeatherRendererRainOcclusionMixin {

    @Redirect(
            method = "spawnRainParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getPrecipitationHeight(II)I"
            ),
            require = 0
    )
    private static int crosstie$redirectAngelicaParticleHeight(WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }
}
