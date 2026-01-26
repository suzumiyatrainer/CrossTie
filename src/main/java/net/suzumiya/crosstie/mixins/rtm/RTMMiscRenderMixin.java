package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTMの各種マシン・装飾・架線の描画最適化
 */
@Mixin(targets = {
        "jp.ngt.rtm.electric.RenderElectricalWiring",
        "jp.ngt.rtm.block.tileentity.RenderMachine",
        "jp.ngt.rtm.block.tileentity.RenderStation",
        "jp.ngt.rtm.block.tileentity.RenderRailroadSign",
        "jp.ngt.rtm.block.tileentity.RenderSignBoard",
        "jp.ngt.rtm.block.tileentity.RenderMovingMachine",
        "jp.ngt.rtm.block.tileentity.RenderFlag",
        "jp.ngt.rtm.block.tileentity.RenderOrnament",
        "jp.ngt.rtm.block.tileentity.RenderPipe",
        "jp.ngt.rtm.block.tileentity.RenderEffect",
        "jp.ngt.rtm.block.tileentity.RenderMirror",
        "jp.ngt.rtm.block.tileentity.RenderPaint"
}, remap = false)
public abstract class RTMMiscRenderMixin extends TileEntitySpecialRenderer {

    // renderTileEntityAtのエイリアス (Deobf / SRG)
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
