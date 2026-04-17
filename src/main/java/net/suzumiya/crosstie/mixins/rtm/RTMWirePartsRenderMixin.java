package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WirePartsRenderer を距離で間引く。
 */
@Mixin(targets = "jp.ngt.rtm.render.WirePartsRenderer", remap = false)
public abstract class RTMWirePartsRenderMixin {

    /**
     * プレイヤーから遠いワイヤーは描画しない。
     */
    @Inject(method = "renderWire(Ljp/ngt/rtm/electric/TileEntityElectricalWiring;Ljp/ngt/rtm/electric/Connection;Ljp/ngt/ngtlib/math/Vec3;FLjp/ngt/rtm/render/RenderPass;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullWireParts(@Coerce Object tileEntity, @Coerce Object connection, @Coerce Object target,
            float par8, @Coerce Object pass, CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4)
            renderChunks = 4;

        double cullDist = renderChunks * 16.0;

        // TileEntity の距離判定は二乗距離
        if (((TileEntity) tileEntity).getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
                mc.renderViewEntity.posZ) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
