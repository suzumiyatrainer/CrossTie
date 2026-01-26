package net.suzumiya.crosstie.mixins.other;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BambooModの描画最適化
 */
@Mixin(targets = {
        "ruby.bamboo.render.tileentity.RenderCampfire",
        "ruby.bamboo.render.tileentity.RenderAndon",
        "ruby.bamboo.render.tileentity.RenderManeki",
        "ruby.bamboo.render.tileentity.RenderMillStone",
        "ruby.bamboo.render.tileentity.RenderHuton",
        "ruby.bamboo.render.tileentity.RenderVillagerBlock"
}, remap = false)
public abstract class BambooRenderMixin extends TileEntitySpecialRenderer {

    @Inject(method = { "renderTileEntityAt", "func_147500_a" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRender(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4)
            renderChunks = 4;

        double cullDist = renderChunks * 16.0;

        // TileEntity.getDistanceFrom returns squared distance
        if (tileEntity.getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
                mc.renderViewEntity.posZ) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
