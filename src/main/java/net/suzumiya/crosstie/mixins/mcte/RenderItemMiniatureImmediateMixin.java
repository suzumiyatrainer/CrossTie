package net.suzumiya.crosstie.mixins.mcte;

import jp.ngt.ngtlib.renderer.NGTRenderer;
import jp.ngt.ngtlib.util.NGTUtilClient;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Implements "True Immediate Mode" for {@code RenderItemMiniature.renderBlocks()}.
 *
 * <p>Similar to the block renderer, the item renderer's display list logic is
 * bypassed to ensure visual correctness under Angelica. Because
 * {@code RenderProp} is a private inner class, we use reflection to access the
 * necessary fields for the immediate draw call.
 */
@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureImmediateMixin {

    @Unique
    private static Field crosstie$ngtoField;
    @Unique
    private static Field crosstie$worldField;
    @Unique
    private static Field crosstie$modeField;

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderItemBlocksImmediately(@Coerce Object prop, float par3, int pass, CallbackInfo ci) {
        try {
            // Lazy initialization of reflection fields
            if (crosstie$ngtoField == null) {
                Class<?> propClass = prop.getClass();
                crosstie$ngtoField = propClass.getField("ngto");
                crosstie$worldField = propClass.getField("world");
                crosstie$modeField = propClass.getField("mode");
            }

            Object ngto = crosstie$ngtoField.get(prop);
            Object world = crosstie$worldField.get(prop);
            int mode = crosstie$modeField.getInt(prop);

            // --- SET UP STATE ---
            NGTUtilClient.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

            boolean smoothing = NGTUtilClient.getMinecraft().gameSettings.ambientOcclusion != 0;
            if (smoothing) {
                GL11.glShadeModel(GL11.GL_SMOOTH);
            }

            // --- EXECUTE IMMEDIATE DRAW ---
            // Bypasses Angelica's incompatible display-list recording for items.
            NGTRenderer.renderNGTObject((jp.ngt.ngtlib.world.IBlockAccessNGT) world, (jp.ngt.ngtlib.block.NGTObject) ngto,
                    true, mode, pass);

            // --- CLEAN UP STATE ---
            if (smoothing) {
                GL11.glShadeModel(GL11.GL_FLAT);
            }

            // --- CANCEL ORIGINAL ---
            ci.cancel();

        } catch (Exception e) {
            // Fallback to original logic if reflection fails (should not happen)
            e.printStackTrace();
        }
    }
}
