package net.suzumiya.crosstie.mixins.angelica;

import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderGlobal.class, priority = 500)
public abstract class AngelicaCloudRenderingFixMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$useShaderClouds(float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        // pipeline が null でなければ（シェーダーが有効なら）バニラ雲をキャンセル
        if (pipeline != null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCloudsFancy", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipVanillaFancyClouds(float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCloudsFast", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipVanillaFastClouds(float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            ci.cancel();
        }
    }
}