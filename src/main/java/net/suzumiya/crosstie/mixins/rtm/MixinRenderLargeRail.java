package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class MixinRenderLargeRail {

    private static Method getRailSizeMethod;

    @Inject(method = "renderTileEntityLargeRail", at = @At("HEAD"), cancellable = true)
    private void crosstie$aggressiveCullRail(@Coerce Object tileObj, double par2, double par4, double par6, float par8, CallbackInfo ci) {
        try {
            if (getRailSizeMethod == null) {
                getRailSizeMethod = tileObj.getClass().getMethod("getRailSize");
            }
            int[] size = (int[]) getRailSizeMethod.invoke(tileObj);
            
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                // Approximate client chunk loading border (Chunk Render Distance + some leeway)
                int renderDistBlocks = mc.gameSettings.renderDistanceChunks * 16 + 16;
                double pX = mc.thePlayer.posX;
                double pZ = mc.thePlayer.posZ;
                
                // Fast Chebyshev distance check of the absolute bounds against player's expanded view distance map
                if ((size[3] + 5.5 < pX - renderDistBlocks) || (size[0] - 3.5 > pX + renderDistBlocks) ||
                    (size[5] + 5.5 < pZ - renderDistBlocks) || (size[2] - 3.5 > pZ + renderDistBlocks)) {
                    // Completely outside player environment - skip GPU and Angelica logic
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors on first fail, allow render safely
        }
    }
}
