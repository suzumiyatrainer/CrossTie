package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
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
 * MCTE ミニチュアのブロック状態レンダリング時の GL ステート汚染を防ぐ。
 *
 * <p>renderTileEntityAt の前後で GL ステートを保存・復元し、
 * McteMiniatureRenderContext を有効化する。これにより AngelicaDisplayListManagerMixin
 * および GLHelperMixin の MCTE バイパスが正しく機能する。
 *
 * <p>注意: renderBlocks は RenderMiniature の private メソッドであるため、
 * @Inject による直接の注入は行わない。GL_COMPILE_AND_EXECUTE の昇格は
 * GLHelperMixin 側で処理する。
 */
@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureContextGuardMixin {

    @Unique
    private static final boolean CROSSTIE_HAS_ANGELICA =
            RenderMiniatureContextGuardMixin.class.getClassLoader()
                    .getResource("com/gtnewhorizons/angelica/glsm/DisplayListManager.class") != null;
    @Unique
    private static Field CROSSTIE_TILE_BLOCKS_OBJECT;
    @Unique
    private static Field CROSSTIE_TILE_MODE;
    @Unique
    private static Field CROSSTIE_MODE_ID;
    @Unique
    private static Method CROSSTIE_RENDER_NGT_OBJECT;

    @Unique
    private static final int CROSSTIE_LIGHTING_ENABLED = 0;
    @Unique
    private static final int CROSSTIE_LIGHTMAP_ENABLED = 1;
    @Unique
    private static final int CROSSTIE_BRIGHTNESS_X = 2;
    @Unique
    private static final int CROSSTIE_BRIGHTNESS_Y = 3;
    @Unique
    private static final ThreadLocal<float[]> CROSSTIE_SAVED_STATE =
            ThreadLocal.withInitial(() -> new float[4]);

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), remap = false)
    private void crosstie$prepareMiniatureRender(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        float[] state = CROSSTIE_SAVED_STATE.get();
        state[CROSSTIE_LIGHTING_ENABLED] = GL11.glIsEnabled(GL11.GL_LIGHTING) ? 1.0F : 0.0F;

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        state[CROSSTIE_LIGHTMAP_ENABLED] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D) ? 1.0F : 0.0F;
        state[CROSSTIE_BRIGHTNESS_X] = OpenGlHelper.lastBrightnessX;
        state[CROSSTIE_BRIGHTNESS_Y] = OpenGlHelper.lastBrightnessY;

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator.instance.setColorOpaque_F(1.0F, 1.0F, 1.0F);

        if (Hi03ExpressRailwayContext.isActive() || Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.reset();
        }

        McteMiniatureRenderContext.enter();
    }

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderBlocksDirect(@Coerce Object world, @Coerce Object tile, float partialTicks, int pass,
            CallbackInfo ci) {
        if (!CROSSTIE_HAS_ANGELICA) {
            return;
        }

        crosstie$initBlockRenderReflection(tile);
        Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.entityRenderer != null) {
            minecraft.entityRenderer.enableLightmap(partialTicks);
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        boolean smoothing = minecraft.gameSettings.ambientOcclusion != 0;
        if (smoothing) {
            GL11.glShadeModel(GL11.GL_SMOOTH);
        }

        Object blocksObject = crosstie$getFieldValue(CROSSTIE_TILE_BLOCKS_OBJECT, tile);
        Object mode = crosstie$getFieldValue(CROSSTIE_TILE_MODE, tile);
        int modeId = crosstie$getIntFieldValue(CROSSTIE_MODE_ID, mode);
        crosstie$invokeRenderNgtObject(world, blocksObject, true, modeId, pass);

        if (smoothing) {
            GL11.glShadeModel(GL11.GL_FLAT);
        }
        minecraft.entityRenderer.enableLightmap(partialTicks);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        ci.cancel();
    }

    @Inject(method = "renderTileEntityAt", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupMiniatureRender(TileEntity tile, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        float[] state = CROSSTIE_SAVED_STATE.get();
        Minecraft mc = Minecraft.getMinecraft();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (state[CROSSTIE_LIGHTING_ENABLED] != 0.0F) GL11.glEnable(GL11.GL_LIGHTING);
        else GL11.glDisable(GL11.GL_LIGHTING);

        if (state[CROSSTIE_LIGHTMAP_ENABLED] != 0.0F) {
            if (mc != null && mc.entityRenderer != null) {
                mc.entityRenderer.enableLightmap(0.0D);
            }
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    state[CROSSTIE_BRIGHTNESS_X],
                    state[CROSSTIE_BRIGHTNESS_Y]);
        } else {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        McteMiniatureRenderContext.exit();
    }

    @Unique
    private static void crosstie$initBlockRenderReflection(Object tile) {
        if (CROSSTIE_RENDER_NGT_OBJECT != null) {
            return;
        }

        try {
            ClassLoader loader = RenderMiniatureContextGuardMixin.class.getClassLoader();
            Class<?> tileClass = tile.getClass();
            CROSSTIE_TILE_BLOCKS_OBJECT = tileClass.getField("blocksObject");
            CROSSTIE_TILE_MODE = tileClass.getField("mode");

            Object mode = CROSSTIE_TILE_MODE.get(tile);
            CROSSTIE_MODE_ID = mode.getClass().getField("id");

            Class<?> rendererClass = Class.forName("jp.ngt.ngtlib.renderer.NGTRenderer", false, loader);
            for (Method method : rendererClass.getMethods()) {
                if ("renderNGTObject".equals(method.getName()) && method.getParameterTypes().length == 5) {
                    CROSSTIE_RENDER_NGT_OBJECT = method;
                    break;
                }
            }

            if (CROSSTIE_RENDER_NGT_OBJECT == null) {
                throw new NoSuchMethodException("renderNGTObject(IBlockAccessNGT, NGTObject, boolean, int, int)");
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize MCTE miniature reflection bridge", e);
        }
    }

    @Unique
    private static void crosstie$invokeRenderNgtObject(Object world, Object ngto, boolean changeLighting, int mode,
            int pass) {
        try {
            CROSSTIE_RENDER_NGT_OBJECT.invoke(null, world, ngto, changeLighting, mode, pass);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke NGTRenderer.renderNGTObject", e);
        }
    }

    @Unique
    private static Object crosstie$getFieldValue(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read miniature field", e);
        }
    }

    @Unique
    private static int crosstie$getIntFieldValue(Field field, Object instance) {
        try {
            return field.getInt(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read miniature int field", e);
        }
    }
}
