package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Implements "True Immediate Mode" for RenderItemMiniature.renderBlocks() without hard-coded dependencies.
 *
 * <p>This version uses reflection to access MCTE/NGTLib classes, keeping the project
 * buildable even in CI environments without the required JARs in the libs folder.
 */
@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureImmediateMixin {

    @Unique
    private static Method crosstie$renderNGTObjectMethod;
    @Unique
    private static Method crosstie$getMinecraftMethod;
    @Unique
    private static Field crosstie$locationBlocksTextureField;
    @Unique
    private static Field crosstie$ngtoField;
    @Unique
    private static Field crosstie$worldField;
    @Unique
    private static Field crosstie$modeField;

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderItemBlocksImmediately(@Coerce Object prop, float par3, int pass, CallbackInfo ci) {
        try {
            // Lazy initialization of reflection handles
            if (crosstie$renderNGTObjectMethod == null) {
                Class<?> ngtRendererClass = Class.forName("jp.ngt.ngtlib.renderer.NGTRenderer");
                Class<?> iBlockAccessClass = Class.forName("jp.ngt.ngtlib.world.IBlockAccessNGT");
                Class<?> ngtObjectClass = Class.forName("jp.ngt.ngtlib.block.NGTObject");
                crosstie$renderNGTObjectMethod = ngtRendererClass.getMethod("renderNGTObject", iBlockAccessClass,
                        ngtObjectClass, boolean.class, int.class, int.class);

                Class<?> ngtUtilClientClass = Class.forName("jp.ngt.ngtlib.util.NGTUtilClient");
                crosstie$getMinecraftMethod = ngtUtilClientClass.getMethod("getMinecraft");

                Class<?> textureMapClass = Class.forName("net.minecraft.client.renderer.texture.TextureMap");
                crosstie$locationBlocksTextureField = textureMapClass.getField("locationBlocksTexture");

                Class<?> propClass = prop.getClass();
                crosstie$ngtoField = propClass.getField("ngto");
                crosstie$worldField = propClass.getField("world");
                crosstie$modeField = propClass.getField("mode");
            }

            Object ngto = crosstie$ngtoField.get(prop);
            Object world = crosstie$worldField.get(prop);
            int mode = crosstie$modeField.getInt(prop);
            Minecraft mc = (Minecraft) crosstie$getMinecraftMethod.invoke(null);

            // --- SET UP STATE ---
            ResourceLocation blocksTex = (ResourceLocation) crosstie$locationBlocksTextureField.get(null);
            mc.getTextureManager().bindTexture(blocksTex);

            boolean smoothing = mc.gameSettings.ambientOcclusion != 0;
            if (smoothing) {
                GL11.glShadeModel(GL11.GL_SMOOTH);
            }

            // --- EXECUTE IMMEDIATE DRAW ---
            crosstie$renderNGTObjectMethod.invoke(null, world, ngto, true, mode, pass);

            // --- CLEAN UP STATE ---
            if (smoothing) {
                GL11.glShadeModel(GL11.GL_FLAT);
            }

            // --- CANCEL ORIGINAL ---
            ci.cancel();

        } catch (Exception e) {
            // Silently skip if anything fails in reflection
        }
    }
}
