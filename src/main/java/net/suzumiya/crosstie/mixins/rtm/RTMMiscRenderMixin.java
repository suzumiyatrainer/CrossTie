package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.EntityPositionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM の各種 TileEntity レンダラをまとめて距離カリングする。
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

    // renderTileEntityAt の別名（Deobf / SRG）
    @Inject(method = { "renderTileEntityAt", "func_147500_a" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRender(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;
        double[] viewerPos = new double[3];
        if (!EntityPositionHelper.tryGetPosition(mc.renderViewEntity, viewerPos))
            return;

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4)
            renderChunks = 4;

        double cullDist = renderChunks * 16.0;

        // TileEntity の距離判定は二乗距離
        if (tileEntity.getDistanceFrom(viewerPos[0], viewerPos[1], viewerPos[2]) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
