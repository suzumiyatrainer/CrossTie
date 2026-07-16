package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.RenderMarkerBlockBase;
import net.suzumiya.crosstie.utils.MarkerRenderState;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderMarkerBlockBase.class, remap = false)
public abstract class RenderMarkerBlockBaseMixin {

    private static int crosstie$savedTextureId = 0;
    private static boolean crosstie$textureStateRedirected = false;

    private static boolean crosstie$isShaderEnabled() {
        if (jp.ngt.ngtlib.util.NGTUtilClient.usingShader()) {
            return true;
        }
        try {
            Class<?> clazz = Class.forName("shadersmod.client.Shaders");
            java.lang.reflect.Field field = clazz.getDeclaredField("shaderPackLoaded");
            return field.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    @Redirect(method = "renderDistanceMark", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void redirectGlDisable(int cap) {
        if (cap == GL11.GL_TEXTURE_2D && crosstie$isShaderEnabled()) {
            crosstie$savedTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, MarkerRenderState.getWhiteTexture());
            crosstie$textureStateRedirected = true;
        } else {
            GL11.glDisable(cap);
        }
    }

    @Redirect(method = "renderDistanceMark", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private void redirectGlEnable(int cap) {
        if (cap == GL11.GL_TEXTURE_2D && crosstie$textureStateRedirected) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, crosstie$savedTextureId);
            crosstie$textureStateRedirected = false;
        } else {
            GL11.glEnable(cap);
        }
    }

    private static int crosstie$savedTextureIdGrid = 0;
    private static boolean crosstie$textureStateRedirectedGrid = false;

    @org.spongepowered.asm.mixin.injection.Inject(method = "renderGrid", at = @At("HEAD"))
    private void onRenderGridHead(jp.ngt.rtm.rail.TileEntityMarker marker, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (crosstie$isShaderEnabled()) {
            crosstie$savedTextureIdGrid = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, MarkerRenderState.getWhiteTexture());
            crosstie$textureStateRedirectedGrid = true;
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "renderGrid", at = @At("RETURN"))
    private void onRenderGridReturn(jp.ngt.rtm.rail.TileEntityMarker marker, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (crosstie$textureStateRedirectedGrid) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, crosstie$savedTextureIdGrid);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            crosstie$textureStateRedirectedGrid = false;
        }
    }
}
