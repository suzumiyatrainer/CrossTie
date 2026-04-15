package net.suzumiya.crosstie.mixins.angelica;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reinitializes Angelica client-array state before RenderGlobal builds startup display lists.
 *
 * Angelica replays Tessellator draws through VertexAttribState during display-list compilation.
 * Startup sky/star display lists are especially sensitive because they run before the first world loads.
 */
@Mixin(value = RenderGlobal.class)
public class RenderGlobalInitMixin {

    @Inject(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushMatrix()V", ordinal = 0, remap = false))
    private void crosstie$resetAngelicaClientState(Minecraft minecraft, CallbackInfo ci) {
        try {
            ClassLoader loader = this.getClass().getClassLoader();
            Class<?> glStateManagerClass = Class.forName(
                    "com.gtnewhorizons.angelica.glsm.GLStateManager",
                    false,
                    loader);
            Class<?> vertexAttribStateClass = Class.forName(
                    "com.gtnewhorizons.angelica.glsm.states.VertexAttribState",
                    false,
                    loader);
            Class<?> shaderManagerClass = Class.forName(
                    "com.gtnewhorizons.angelica.glsm.ffp.ShaderManager",
                    false,
                    loader);

            Field defaultVaoField = glStateManagerClass.getDeclaredField("defaultVAO");
            defaultVaoField.setAccessible(true);
            int defaultVao = defaultVaoField.getInt(null);

            Method bindVertexArray = glStateManagerClass.getMethod("glBindVertexArray", int.class);
            Method disableClientState = glStateManagerClass.getMethod("glDisableClientState", int.class);
            Method clientActiveTexture = glStateManagerClass.getMethod("glClientActiveTexture", int.class);
            Method disableAttrib = glStateManagerClass.getMethod("glDisableVertexAttribArray", int.class);
            Method resetAttribState = vertexAttribStateClass.getMethod("reset");
            Method initAttribState = vertexAttribStateClass.getMethod("init", int.class);
            Method getShaderManager = shaderManagerClass.getMethod("getInstance");
            Method setCurrentVertexFlags = shaderManagerClass.getMethod("setCurrentVertexFlags", int.class);

            resetAttribState.invoke(null);
            initAttribState.invoke(null, defaultVao);
            bindVertexArray.invoke(null, 0);
            setCurrentVertexFlags.invoke(getShaderManager.invoke(null), 0);

            disableClientState.invoke(null, GL11.GL_VERTEX_ARRAY);
            disableClientState.invoke(null, GL11.GL_COLOR_ARRAY);
            disableClientState.invoke(null, GL11.GL_NORMAL_ARRAY);

            clientActiveTexture.invoke(null, GL13.GL_TEXTURE0);
            disableClientState.invoke(null, GL11.GL_TEXTURE_COORD_ARRAY);
            clientActiveTexture.invoke(null, GL13.GL_TEXTURE1);
            disableClientState.invoke(null, GL11.GL_TEXTURE_COORD_ARRAY);
            clientActiveTexture.invoke(null, GL13.GL_TEXTURE0);

            for (int i = 0; i < 16; i++) {
                disableAttrib.invoke(null, i);
            }
        } catch (ReflectiveOperationException ignored) {
            // Angelica absent or internals changed; skip the cleanup.
        }
    }
}
