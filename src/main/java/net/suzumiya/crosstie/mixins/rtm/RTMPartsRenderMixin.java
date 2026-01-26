package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTMのパーツ描画クラス(jp.ngt.rtm.renderパッケージ)の最適化
 * Renderクラスからの呼び出し漏れや、複雑な呼び出し階層に対応するための二重カリング
 */
@Mixin(targets = {
        "jp.ngt.rtm.render.RailPartsRenderer",
        "jp.ngt.rtm.render.WirePartsRenderer"
}, remap = false)
public abstract class RTMPartsRenderMixin {

    // renderRail(TileEntityLargeRailCore, int, double, double, double, float)
    @Inject(method = "renderRail", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRailParts(Object tileEntity, int index, double x, double y, double z, float par8,
            CallbackInfo ci) {
        cullCommon(tileEntity, ci);
    }

    // renderWire(TileEntityElectricalWiring, Connection, Vec3, float, RenderPass)
    @Inject(method = "renderWire", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullWireParts(Object tileEntity, Object connection, Object target, float par8, Object pass,
            CallbackInfo ci) {
        cullCommon(tileEntity, ci);
    }

    private void cullCommon(Object teObj, CallbackInfo ci) {
        if (!(teObj instanceof TileEntity))
            return;
        TileEntity tileEntity = (TileEntity) teObj;

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
