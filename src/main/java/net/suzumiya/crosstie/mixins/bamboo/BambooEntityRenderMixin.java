package net.suzumiya.crosstie.mixins.bamboo;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bamboo Mod の Entity 描画を距離で間引く。
 *
 * 飛翔系や装飾系の Entity を、描画距離の外では描画しないようにする。
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

        // 描画距離 + 2 チャンクの範囲まで許可する
        double cullDist = (renderChunks + 2) * 16.0;

        // Entity の距離判定は二乗距離で行う
        if (entity.getDistanceSqToEntity(mc.renderViewEntity) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
