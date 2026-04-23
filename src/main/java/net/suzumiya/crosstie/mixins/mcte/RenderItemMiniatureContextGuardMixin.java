package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
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
 * MCTE ミニチュアのアイテム/インベントリレンダリング時の GL ステート汚染を防ぐ。
 *
 * <p>renderItem の前後で GL ステートを保存・復元する。
 * renderBlocks は RenderItemMiniature の private メソッドなので直接注入せず、
 * McteMiniatureRenderContext を renderItem 全体でカバーすることで
 * GLHelperMixin の GL_COMPILE_AND_EXECUTE バイパスが機能するようにする。
 */
@Mixin(targets = "jp.ngt.mcte.item.RenderItemMiniature", remap = false)
public abstract class RenderItemMiniatureContextGuardMixin {

    @Unique
    private static final boolean CROSSTIE_HAS_ANGELICA =
            RenderItemMiniatureContextGuardMixin.class.getClassLoader()
                    .getResource("com/gtnewhorizons/angelica/glsm/DisplayListManager.class") != null;
    @Unique
    private static Field CROSSTIE_RENDER_PROP_WORLD;
    @Unique
    private static Field CROSSTIE_RENDER_PROP_NGTO;
    @Unique
    private static Field CROSSTIE_RENDER_PROP_MODE;
    @Unique
    private static Method CROSSTIE_RENDER_NGT_OBJECT;

    @Unique
    private static final int CROSSTIE_LIGHTMAP_ENABLED = 0;
    @Unique
    private static final int CROSSTIE_BRIGHTNESS_X = 1;
    @Unique
    private static final int CROSSTIE_BRIGHTNESS_Y = 2;
    @Unique
    private static final ThreadLocal<float[]> CROSSTIE_SAVED_STATE =
            ThreadLocal.withInitial(() -> new float[3]);

    @Inject(method = "renderItem", at = @At("HEAD"), remap = false)
    private void crosstie$captureRenderItemState(IItemRenderer.ItemRenderType type, ItemStack item, Object[] data,
                                                 CallbackInfo ci) {
        float[] state = CROSSTIE_SAVED_STATE.get();
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

        McteMiniatureRenderContext.enter();
    }

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderBlocksDirect(@Coerce Object prop, float partialTicks, int pass, CallbackInfo ci) {
        if (!CROSSTIE_HAS_ANGELICA) {
            return;
        }

        crosstie$initRenderPropFields(prop);
        Object world = crosstie$getFieldValue(CROSSTIE_RENDER_PROP_WORLD, prop);
        Object ngto = crosstie$getFieldValue(CROSSTIE_RENDER_PROP_NGTO, prop);
        int mode = crosstie$getIntFieldValue(CROSSTIE_RENDER_PROP_MODE, prop);

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.entityRenderer != null) {
            minecraft.entityRenderer.enableLightmap(partialTicks);
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        boolean smoothing = minecraft.gameSettings.ambientOcclusion != 0;
        if (smoothing) {
            GL11.glShadeModel(GL11.GL_SMOOTH);
        }

        crosstie$invokeRenderNgtObject(world, ngto, true, mode, pass);

        if (smoothing) {
            GL11.glShadeModel(GL11.GL_FLAT);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_LIGHTING);
        minecraft.entityRenderer.enableLightmap(partialTicks);
        ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("RETURN"), remap = false)
    private void crosstie$restoreRenderItemState(IItemRenderer.ItemRenderType type, ItemStack item, Object[] data,
                                                 CallbackInfo ci) {
        float[] state = CROSSTIE_SAVED_STATE.get();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (state[CROSSTIE_LIGHTMAP_ENABLED] != 0.0F) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    state[CROSSTIE_BRIGHTNESS_X],
                    state[CROSSTIE_BRIGHTNESS_Y]);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        McteMiniatureRenderContext.exit();
    }

    @Unique
    private static void crosstie$initRenderPropFields(Object prop) {
        if (CROSSTIE_RENDER_PROP_WORLD != null && CROSSTIE_RENDER_NGT_OBJECT != null) {
            return;
        }

        try {
            ClassLoader loader = RenderItemMiniatureContextGuardMixin.class.getClassLoader();
            Class<?> propClass = prop.getClass();
            CROSSTIE_RENDER_PROP_WORLD = propClass.getField("world");
            CROSSTIE_RENDER_PROP_NGTO = propClass.getField("ngto");
            CROSSTIE_RENDER_PROP_MODE = propClass.getField("mode");

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
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to resolve RenderItemMiniature.RenderProp fields", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize RenderItemMiniature reflection bridge", e);
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
            throw new RuntimeException("Failed to read RenderItemMiniature.RenderProp field", e);
        }
    }

    @Unique
    private static int crosstie$getIntFieldValue(Field field, Object instance) {
        try {
            return field.getInt(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read RenderItemMiniature.RenderProp int field", e);
        }
    }
}
