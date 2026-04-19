package net.suzumiya.crosstie.mixins.angelica;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.AngelicaRenderGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderGlobal.class)
public class RenderGlobalFrameTickMixin {

    @Inject(method = "renderEntities", at = @At("HEAD"), remap = false)
    private void crosstie$tickFallbackWindow(EntityLivingBase viewer, ICamera camera, float partialTicks, CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard) {
            AngelicaRenderGuard.tickFrame();
        }
    }
}
