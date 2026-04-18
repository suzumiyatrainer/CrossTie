package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class MixinRenderElectricalWiring {

    @Inject(method = "renderElectricalWiring", at = @At("HEAD"), cancellable = true)
    private void crosstie$aggressiveCullWire(@Coerce Object tileObj, double par2, double par4, double par6, float par8, CallbackInfo ci) {
        TileEntity tile = (TileEntity) tileObj;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            // Wires in RTM are nominally short (< 64). We over-estimate to 128 to be safe against long wire glitches.
            int renderRangeBlocks = mc.gameSettings.renderDistanceChunks * 16 + 128;
            double dX = tile.xCoord - mc.thePlayer.posX;
            double dZ = tile.zCoord - mc.thePlayer.posZ;
            
            if (dX * dX + dZ * dZ > renderRangeBlocks * renderRangeBlocks) {
                // If it pushes too far into the distance, mathematically cull it to save GL loop overhead
                ci.cancel();
            }
        }
    }
}
