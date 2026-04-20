package net.suzumiya.crosstie.mixins.mcte;

import jp.ngt.mcte.block.TileEntityMiniature;
import jp.ngt.mcte.world.MCTEWorld;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.renderer.NGTRenderer;
import jp.ngt.ngtlib.util.NGTUtilClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements "True Immediate Mode" for {@code RenderMiniature.renderBlocks()}.
 *
 * <p>By bypassing the display list logic entirely, we fix the "missing blocks"
 * issue caused by Angelica's incorrect side-culling logic for dummy MCTE worlds.
 * Drawing directly every frame ensures 100% visual correctness with minimal
 * overhead for modern hardware.
 */
@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureImmediateMixin extends TileEntitySpecialRenderer {

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderBlocksImmediately(MCTEWorld world, TileEntityMiniature tile, float par3, int pass,
            CallbackInfo ci) {
        // --- 1. SET UP RENDERING STATE ---
        GLHelper.disableLighting();
        int brightness = tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0);
        GLHelper.setBrightness(brightness);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        this.bindTexture(TextureMap.locationBlocksTexture);

        boolean smoothing = NGTUtilClient.getMinecraft().gameSettings.ambientOcclusion != 0;
        if (smoothing) {
            GL11.glShadeModel(GL11.GL_SMOOTH);
        }

        // --- 2. EXECUTE IMMEDIATE DRAW ---
        // Instead of recording/calling a display list, we call the draw code directly every frame.
        // This bypasses all problematic cache/culling logic in Angelica's DisplayListManager.
        NGTRenderer.renderNGTObject(world, tile.blocksObject, false, tile.mode.id, pass);

        // --- 3. CLEAN UP STATE ---
        if (smoothing) {
            GL11.glShadeModel(GL11.GL_FLAT);
        }

        GLHelper.enableLighting();
        if (NGTUtilClient.getMinecraft().entityRenderer != null) {
            NGTUtilClient.getMinecraft().entityRenderer.enableLightmap(par3);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // --- 4. CANCEL ORIGINAL METHOD ---
        // Skip the original display list logic (GLHelper.startCompile / glCallList).
        ci.cancel();
    }
}
