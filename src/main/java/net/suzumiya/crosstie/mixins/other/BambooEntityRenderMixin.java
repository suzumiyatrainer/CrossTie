package net.suzumiya.crosstie.mixins.other;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BambooModのエンティティ描画最適化
 * 蛍、風車、水車などのエンティティ描画を描画距離外でスキップします。
 */
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4)
            renderChunks = 4;

        // 描画距離 + 2チャンク (Entityは移動するため少し余裕を持たせる)
        double cullDist = (renderChunks + 2) * 16.0;

        // Entity.getDistanceSq returns squared distance (This is correct for Entity)
        if (entity.getDistanceSqToEntity(mc.renderViewEntity) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
