package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.rail.RenderMarkerBlock1122;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import jp.ngt.rtm.rail.TileEntityMarker;
import jp.ngt.rtm.rail.RenderMarkerBlock1122.MarkerElement;

@Mixin(value = RenderMarkerBlock1122.class, remap = false)
public class RenderMarkerBlock1122MathPickingMixin {

    private static int lastLoadedName = -1;
    private static int closestName = -1;
    private static float closestZ = Float.MAX_VALUE;
    private static boolean currentPickMode = false;

    private static FloatBuffer modelMatrix = BufferUtils.createFloatBuffer(16);
    private static FloatBuffer projMatrix = BufferUtils.createFloatBuffer(16);
    private static IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private static FloatBuffer winPos1 = BufferUtils.createFloatBuffer(3);
    private static FloatBuffer winPos2 = BufferUtils.createFloatBuffer(3);

    @Inject(method = "renderAnchorLine", at = @At("HEAD"))
    private void onRenderAnchorLineHead(TileEntityMarker marker, boolean isPickMode, MarkerElement hoveredElement, CallbackInfoReturnable<MarkerElement> cir) {
        net.suzumiya.crosstie.utils.MarkerRenderState.isMarkerRendering = true;
        currentPickMode = isPickMode;
        if (isPickMode) {
            closestName = -1;
            closestZ = Float.MAX_VALUE;
            // AngelicaのGLStateManagerからの行列取得を試みる
            try {
                Class<?> glStateManagerClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                java.lang.reflect.Method glGetFloatMethod = glStateManagerClass.getMethod("glGetFloat", int.class, FloatBuffer.class);
                
                projMatrix.clear();
                glGetFloatMethod.invoke(null, GL11.GL_PROJECTION_MATRIX, projMatrix);
            } catch (Exception e) {
                projMatrix.clear();
                GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projMatrix);
            }
            viewport.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        }
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/renderer/GLHelper;startMousePicking(F)V", remap = false))
    private void redirectStartMousePicking(float range) {
        // OptiFine環境で GL_SELECT や gluPickMatrix が適用されると行列が破壊され文字が巨大化するため、完全にバイパスする
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/renderer/GLHelper;finishMousePicking()I", remap = false))
    private int redirectFinishMousePicking() {
        return 0; // 常に0を返して元の判定ロジックをスキップさせる
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadName(I)V", remap = false))
    private void redirectGlLoadName(int name) {
        lastLoadedName = name;
    }

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

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void redirectGlDisable(int cap) {
        if (cap == GL11.GL_TEXTURE_2D && !currentPickMode && crosstie$isShaderEnabled()) {
            crosstie$savedTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.suzumiya.crosstie.utils.MarkerRenderState.getWhiteTexture());
            crosstie$textureStateRedirected = true;
        } else {
            GL11.glDisable(cap);
        }
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private void redirectGlEnable(int cap) {
        if (cap == GL11.GL_TEXTURE_2D && crosstie$textureStateRedirected) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, crosstie$savedTextureId);
            crosstie$textureStateRedirected = false;
        } else {
            GL11.glEnable(cap);
        }
    }

    @Inject(method = "renderLine", at = @At("HEAD"), cancellable = true)
    private void onRenderLine(float startX, float startY, float startZ, float endX, float endY, float endZ, int color, CallbackInfo ci) {
        if (currentPickMode) { // フラグによる確実な判定
            try {
                Class<?> glStateManagerClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                java.lang.reflect.Method glGetFloatMethod = glStateManagerClass.getMethod("glGetFloat", int.class, FloatBuffer.class);
                modelMatrix.clear();
                glGetFloatMethod.invoke(null, GL11.GL_MODELVIEW_MATRIX, modelMatrix);
            } catch (Exception e) {
                modelMatrix.clear();
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelMatrix);
            }
            
            winPos1.clear();
            winPos2.clear();
            GLU.gluProject(startX, startY, startZ, modelMatrix, projMatrix, viewport, winPos1);
            GLU.gluProject(endX, endY, endZ, modelMatrix, projMatrix, viewport, winPos2);

            float px1 = winPos1.get(0);
            float py1 = winPos1.get(1);
            float pz1 = winPos1.get(2);

            float px2 = winPos2.get(0);
            float py2 = winPos2.get(1);
            float pz2 = winPos2.get(2);

            float mouseX = (float) org.lwjgl.opengl.Display.getWidth() / 2.0F;
            float mouseY = (float) org.lwjgl.opengl.Display.getHeight() / 2.0F;

            if ((pz1 >= -0.1f && pz1 <= 1.1f) || (pz2 >= -0.1f && pz2 <= 1.1f)) {
                float l2 = (px2 - px1) * (px2 - px1) + (py2 - py1) * (py2 - py1);
                float t = 0;
                if (l2 > 0.0001f) {
                    t = Math.max(0, Math.min(1, ((mouseX - px1) * (px2 - px1) + (mouseY - py1) * (py2 - py1)) / l2));
                }
                float projX = px1 + t * (px2 - px1);
                float projY = py1 + t * (py2 - py1);
                float dist = (float) Math.sqrt((mouseX - projX) * (mouseX - projX) + (mouseY - projY) * (mouseY - projY));

                float threshold = org.lwjgl.opengl.Display.getHeight() * 0.05f;

                if (dist < threshold) {
                    float avgZ = (pz1 + pz2) / 2.0f;
                    if (avgZ < closestZ) {
                        closestZ = avgZ;
                        closestName = lastLoadedName;
                    }
                }
            }
            ci.cancel(); // ピッキングモード時は描画処理をスキップしてゴースト表示を防ぐ
        }
    }

    @Inject(method = "renderAnchorLine", at = @At("RETURN"), cancellable = true)
    private void onRenderAnchorLineReturn(TileEntityMarker marker, boolean isPickMode, MarkerElement hoveredElement, CallbackInfoReturnable<MarkerElement> cir) {
        net.suzumiya.crosstie.utils.MarkerRenderState.isMarkerRendering = false;
        if (isPickMode) {
            if (closestName != -1) {
                cir.setReturnValue(MarkerElement.values()[closestName]);
            } else {
                cir.setReturnValue(MarkerElement.NONE);
            }
        }
    }
}
