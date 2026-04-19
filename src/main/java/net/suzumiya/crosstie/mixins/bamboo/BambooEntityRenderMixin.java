package net.suzumiya.crosstie.mixins.bamboo;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "ruby.bamboo.render.RenderFirefly",
        "ruby.bamboo.render.RenderWindmill",
        "ruby.bamboo.render.RenderWaterwheel",
        "ruby.bamboo.render.RenderPetal",
        "ruby.bamboo.render.RenderObon",
        "ruby.bamboo.render.RenderKakeziku",
        "ruby.bamboo.render.RenderKaginawa",
        "ruby.bamboo.render.RenderBSpear"
}, remap = false)
public abstract class BambooEntityRenderMixin extends Render {

    @Inject(method = { "doRender", "func_76986_a" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRender(Entity entity, double x, double y, double z, float yaw, float partialTicks,
            CallbackInfo ci) {
        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        final double cullDist = (renderChunks + 2) * 16.0D;
        final double distSq = x * x + y * y + z * z;
        if (distSq > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
