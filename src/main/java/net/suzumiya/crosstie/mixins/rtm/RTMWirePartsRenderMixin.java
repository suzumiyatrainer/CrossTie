package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WirePartsRenderer optimization mixin.
 * Performs distance-based culling for wire rendering.
 */
@Mixin(targets = "jp.ngt.rtm.render.WirePartsRenderer", remap = false)
public abstract class RTMWirePartsRenderMixin {

    /**
     * Cull wires that are too far from the player.
     * Uses exact parameter types as required by Mixin.
     */
    @Inject(method = "renderWire", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullWireParts(TileEntity tileEntity, CallbackInfo ci) {
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

        // TileEntity.getDistanceFrom returns squared distance
        if (tileEntity.getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
                mc.renderViewEntity.posZ) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
