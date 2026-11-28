package net.suzumiya.crosstie.mixins.minecraft;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.world.World;
import net.suzumiya.crosstie.client.weather.VehicleWeatherManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * バニラ / OptiFine / Angelica 非 Instanced フォールバック環境での雨・雪描画および
 * 雨パーティクル生成時に、RTM 車両の屋根による遮蔽高さを反映する Mixin。
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererRainOcclusionMixin {

    @Inject(method = {"renderRainSnow", "func_78474_d"}, at = @At("HEAD"), require = 0)
    private void crosstie$onRenderRainSnowHead(float partialTicks, CallbackInfo ci) {
        VehicleWeatherManager.setPartialTicks(partialTicks);
    }

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
    private int crosstie$redirectPrecipitationHeightClient(WorldClient world, int x, int z) {
        int defaultHeight = world.getPrecipitationHeight(x, z);
        return VehicleWeatherManager.getOccludedPrecipitationHeight(world, x, z, defaultHeight);
    }
}
