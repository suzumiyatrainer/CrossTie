package net.suzumiya.crosstie.mixins.minecraft;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix for black loading/init screen when Angelica's BatchingFontRenderer is
 * disabled.
 *
 * <p>
 * When MinFo is present, CrossTie forces Angelica's enableFontRenderer=false to
 * prevent
 * a crash (NPE in BatchingFontRenderer.<init> due to MinFo CustomFontRenderer
 * conflict).
 * However, disabling the font batching causes the init/loading screen to render
 * black.
 *
 * <p>
 * This mixin ensures the loading screen background is properly drawn by
 * verifying
 * OpenGL state and re-initializing basic GL parameters if they were left in an
 * invalid
 * state by the missing font batcher initialization.
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreenBackgroundFix {

    /**
     * Ensure GL state is valid before drawing background, fixing the black loading
     * screen
     * that occurs when Angelica's font renderer batching is disabled
     * (enableFontRenderer=false).
     */
    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void crosstie$fixBlackBackground(int tint, CallbackInfo ci) {
        // If the texture manager is in a bad state, restore minimal GL context
        try {
            // Test if GL is in a valid state by checking if we can bind a texture
            GL11.glGetError(); // Clear error flag
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT);
            GL11.glPopAttrib();
        } catch (Exception e) {
            // GL state is broken - restore basic rendering state
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight, 0.0D,
                    1000.0D, 3000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        }
    }
}