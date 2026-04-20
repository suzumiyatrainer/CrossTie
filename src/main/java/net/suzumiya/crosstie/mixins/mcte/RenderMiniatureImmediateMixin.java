package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
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
 * Implements "True Immediate Mode" for RenderMiniature.renderBlocks() without hard-coded dependencies.
 *
 * <p>This version uses reflection to access MCTE/NGTLib classes, allowing CrossTie to compile 
 * even if the target mod JARs are not present on the build classpath (e.g. in CI environments).
 */
@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureImmediateMixin extends TileEntitySpecialRenderer {

    @Unique
    private static Method crosstie$renderNGTObjectMethod;
    @Unique
    private static Method crosstie$getMinecraftMethod;
    @Unique
    private static Field crosstie$locationBlocksTextureField;
    @Unique
    private static Field crosstie$blocksObjectField;
    @Unique
    private static Field crosstie$modeField;
    @Unique
    private static Field crosstie$modeIdField;

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderBlocksImmediately(@Coerce Object world, @Coerce Object tile, float par3, int pass,
            CallbackInfo ci) {
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

                Class<?> tileMiniatureClass = Class.forName("jp.ngt.mcte.block.TileEntityMiniature");
                crosstie$blocksObjectField = tileMiniatureClass.getField("blocksObject");
                crosstie$modeField = tileMiniatureClass.getField("mode");
                crosstie$modeIdField = crosstie$modeField.getType().getField("id");
            }

            TileEntity te = (TileEntity) tile;
            Object blocksObject = crosstie$blocksObjectField.get(tile);
            Object mode = crosstie$modeField.get(tile);
            int modeId = crosstie$modeIdField.getInt(mode);
            Object mc = crosstie$getMinecraftMethod.invoke(null);

            // --- 1. SET UP RENDERING STATE ---
            GL11.glDisable(GL11.GL_LIGHTING);
            int brightness = te.getWorldObj().getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0);
            // GLHelper.setBrightness(brightness) -> simplified openGL call
            int x = brightness & 0xFFFF;
            int y = brightness >> 16;
            // OpenGlHelper.setLightmapTextureCoords(...) is in vanilla, we can use it directly
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                    net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, (float) x, (float) y);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            ResourceLocation blocksTex = (ResourceLocation) crosstie$locationBlocksTextureField.get(null);
            this.bindTexture(blocksTex);

            // Accessing vanilla Minecraft fields via reflected MC instance
            net.minecraft.client.Minecraft realMc = (net.minecraft.client.Minecraft) mc;
            boolean smoothing = realMc.gameSettings.ambientOcclusion != 0;
            if (smoothing) {
                GL11.glShadeModel(GL11.GL_SMOOTH);
            }

            // --- 2. EXECUTE IMMEDIATE DRAW ---
            crosstie$renderNGTObjectMethod.invoke(null, world, blocksObject, false, modeId, pass);

            // --- 3. CLEAN UP STATE ---
            if (smoothing) {
                GL11.glShadeModel(GL11.GL_FLAT);
            }

            GL11.glEnable(GL11.GL_LIGHTING);
            if (realMc.entityRenderer != null) {
                realMc.entityRenderer.enableLightmap(par3);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // --- 4. CANCEL ORIGINAL METHOD ---
            ci.cancel();

        } catch (Exception e) {
            // If anything fails in reflection (e.g. MCTE not installed), skip and let original code handle errors
        }
    }
}
