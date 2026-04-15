package net.suzumiya.crosstie.mixins.rtm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PartsRenderer script rendering context fix for Angelica compatibility.
 * 
 * Ensures OpenGL context is properly initialized before JavaScript script
 * execution in PartsRenderer.render(), preventing "Function is not supported"
 * exceptions when scripts call GL11.glPushMatrix() and other OpenGL functions.
 * 
 * This addresses the crash:
 * java.lang.IllegalStateException: Function is not supported
 * at org.lwjgl.BufferChecks.checkFunctionAddress()
 * at org.lwjgl.opengl.GL11.glPushMatrix()
 * 
 * Root cause: Angelica modifies OpenGL context management, and JavaScript
 * scripts in PartsRenderer need special initialization to access GL11 directly.
 */
@Mixin(targets = "jp.ngt.rtm.render.PartsRenderer", remap = false)
public class PartsRendererScriptContextMixin {

    private static final int MAX_GL_ERROR_DRAIN_ATTEMPTS = 16;

    /**
     * Ensures GL context is ready before any GL calls in PartsRenderer.render().
     * This is called once at the beginning of the render method.
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void crosstie$prepareGLContextForRender(Object t, int pass, float partialTick, CallbackInfo ci) {
        ensureGLContextReady();
    }

    /**
     * Additional safety check: clear GL errors before script execution.
     * This prevents accumulated errors from blocking glPushMatrix() and other GL
     * calls.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/render/PartsRenderer;execScriptFunc(Ljava/lang/String;[Ljava/lang/Object;)V", ordinal = 0), remap = false)
    private void crosstie$clearGLErrorsBeforeScriptExecution(Object t, int pass, float partialTick, CallbackInfo ci) {
        // Clear a small number of pending errors, but never spin forever if the
        // driver/context keeps returning an error state.
        for (int i = 0; i < MAX_GL_ERROR_DRAIN_ATTEMPTS; i++) {
            if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                break;
            }
        }
    }

    /**
     * Ensures OpenGL context is fully initialized and ready for GL11 calls.
     * Handles both Angelica and vanilla Minecraft rendering paths.
     */
    private static void ensureGLContextReady() {
        try {
            // Method 1: Force Angelica's GLStateManager to initialize (if present)
            try {
                Class<?> glStateManagerClass = Class.forName(
                        "com.gtnewhorizons.angelica.glsm.GLStateManager",
                        false,
                        PartsRendererScriptContextMixin.class.getClassLoader());

                // Invoke glClientActiveTexture to ensure context is active
                glStateManagerClass.getDeclaredMethod("glClientActiveTexture", int.class)
                        .invoke(null, GL13.GL_TEXTURE0);
            } catch (ClassNotFoundException ignored) {
                // Angelica not present - vanilla rendering
            } catch (ReflectiveOperationException e) {
                // Try alternative method if first one fails
            }

            // Method 2: Manually validate GL context by testing basic operations
            // This serves as both initialization and validation
            try {
                // Test that glPushMatrix is available
                GL11.glPushMatrix();
                GL11.glPopMatrix();
            } catch (IllegalStateException e) {
                // GL context is not ready
                // Log it but continue - the actual GL call in script will provide proper error
                // System.err.println("[CrossTie] GL context not ready: " + e.getMessage());
            }

            // Method 3: Clear any pending GL errors
            for (int i = 0; i < MAX_GL_ERROR_DRAIN_ATTEMPTS; i++) {
                if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                    break;
                }
            }
        } catch (Exception e) {
            // Silently ignore - if GL dies, the actual GL call will provide proper error
            // message
        }
    }
}
