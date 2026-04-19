package net.suzumiya.crosstie.util;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;

/**
 * Applies optional shader-side fix flags when supported by the active shader.
 * Missing uniforms are ignored to keep compatibility with KaizPatchX defaults.
 */
public final class AngelicaShaderFlagBridge {

    private static final String U_FIX_HALF_TRANSPARENT = "ctFixHalfTransparent";
    private static final String U_IFTTT_DIRTY = "ctIfTTTDirty";
    private static final String U_FALLBACK_ACTIVE = "ctFallbackActive";

    private AngelicaShaderFlagBridge() {
    }

    public static void applyFlags(boolean fixHalfTransparent, boolean iftttDirty, boolean fallbackActive) {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program <= 0) {
            return;
        }

        setUniform(program, U_FIX_HALF_TRANSPARENT, fixHalfTransparent ? 1 : 0);
        setUniform(program, U_IFTTT_DIRTY, iftttDirty ? 1 : 0);
        setUniform(program, U_FALLBACK_ACTIVE, fallbackActive ? 1 : 0);
    }

    private static void setUniform(int program, String uniformName, int value) {
        int location = GL20.glGetUniformLocation(program, uniformName);
        if (location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }
}
