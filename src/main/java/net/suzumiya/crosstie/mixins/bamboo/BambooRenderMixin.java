package net.suzumiya.crosstie.mixins.bamboo;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        final double cullDist = renderChunks * 16.0D;
        final double distSq = x * x + y * y + z * z;
        if (distSq > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
