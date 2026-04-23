package net.suzumiya.crosstie.mixins.rtm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.AngelicaRenderGuard;
import net.suzumiya.crosstie.util.EntityPositionHelper;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import net.suzumiya.crosstie.util.RailAabbResolver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * renderRail を明示的に後始末する形で再実装し、hi03 の状態漏れを防ぐ。
 */
@Mixin(targets = "jp.ngt.rtm.render.RailPartsRenderer", remap = false)
public abstract class RTMRailPartsRenderSafeMixin {

    @Unique
    private static final String TARGET_CLASS_NAME = "jp.ngt.rtm.rail.TileEntityLargeRailCore";
    @Unique
    private static final String[] CROSSTIE_RAIL_INDEX_FIELD_CANDIDATES = {
            "currentRailIndex",
            "railIndex",
            "currentIndex",
            "index"
    };
    @Unique
    private static final int CROSSTIE_GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;
    @Unique
    private static final int CROSSTIE_AABB_CULL_MARGIN_CHUNKS = 3;
    @Unique
    private static final Map<String, Method> CROSSTIE_METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    @Unique
    private static final Map<String, Field> CROSSTIE_FIELD_CACHE = new ConcurrentHashMap<String, Field>();
    @Unique
    private static final Map<String, Method> CROSSTIE_RENDER_METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    @Unique
    private static volatile Field CROSSTIE_RAIL_INDEX_FIELD;
    @Unique
    private static volatile boolean CROSSTIE_RAIL_INDEX_FIELD_RESOLVED;
    @Unique
    private static volatile Class<?> CROSSTIE_RAIL_CORE_CLASS;

    @Inject(method = "renderRail(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;IDDDF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderRailSafely(@Coerce Object tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }

        if (CrossTieConfig.enableAngelicaFallbackGuard
                && (AngelicaRenderGuard.hasInvalidDouble(x)
                || AngelicaRenderGuard.hasInvalidDouble(y)
                || AngelicaRenderGuard.hasInvalidDouble(z))) {
            AngelicaRenderGuard.triggerFallback();
            return;
        }

        TileEntity railTile = (TileEntity) tileEntity;
        if (this.crosstie$shouldCullRail(railTile)) {
            ci.cancel();
            return;
        }

        if (!this.crosstie$isHi03Rail(railTile)) {
            return;
        }

        boolean glStateCaptured = false;
        boolean renderedSuccessfully = false;
        try {
            glStateCaptured = this.crosstie$captureGLState();
            this.crosstie$setCurrentRailIndex(index);
            Hi03ExpressRailwayContext.enter();

            this.crosstie$invokeRailRenderer("renderRailStatic", railTile, x, y, z, par8);
            this.crosstie$invokeRailRenderer("renderRailDynamic", railTile, x, y, z, par8);
            renderedSuccessfully = true;
        } catch (Exception e) {
            if (CrossTieConfig.enableAngelicaFallbackGuard) {
                AngelicaRenderGuard.triggerFallback();
            }
        } finally {
            if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
                try {
                    GL11.glEndList();
                } catch (RuntimeException ignored) {
                    // ネイティブの list を開いたまま中断した場合の最終手段の後始末
                }
            }
            Hi03ExpressRailwayContext.reset();
            this.crosstie$restoreGLState(glStateCaptured);
        }

        if (renderedSuccessfully) {
            ci.cancel();
        }
    }

    private boolean crosstie$isHi03Rail(TileEntity tileEntity) {
        try {
            String railModel = this.crosstie$getRailModel(tileEntity);
            return railModel != null && railModel.contains("hi03ExpressRailway");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean crosstie$shouldCullRail(TileEntity tileEntity) {
        if (!CrossTieConfig.enableRenderCulling) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null) {
            return false;
        }
        double[] viewerPos = new double[3];
        if (!EntityPositionHelper.tryGetPosition(mc.renderViewEntity, viewerPos)) {
            return false;
        }

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        AxisAlignedBB railAabb = RailAabbResolver.getEffectiveRailAabb(tileEntity, tileEntity.getRenderBoundingBox());
        if (railAabb == null) {
            // Unknown rail bounds must not fall back to tile origin distance.
            return false;
        }

        double aabbCullDist = (renderChunks + CROSSTIE_AABB_CULL_MARGIN_CHUNKS) * 16.0D;
        double aabbCullDistSq = aabbCullDist * aabbCullDist;
        return RailAabbResolver.distanceSqToAabb(viewerPos[0], viewerPos[1], viewerPos[2], railAabb) > aabbCullDistSq;
    }

    @Unique
    private Method crosstie$findMethod(Class<?> owner, String name) throws NoSuchMethodException {
        String cacheKey = owner.getName() + "#" + name;
        Method cached = CROSSTIE_METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                CROSSTIE_METHOD_CACHE.put(cacheKey, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Unique
    private Field crosstie$findField(Class<?> owner, String name) throws NoSuchFieldException {
        String cacheKey = owner.getName() + "#" + name;
        Field cached = CROSSTIE_FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                CROSSTIE_FIELD_CACHE.put(cacheKey, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Unique
    private void crosstie$setCurrentRailIndex(int index) {
        Field railIndexField = this.crosstie$getRailIndexField();
        if (railIndexField == null) {
            return;
        }

        try {
            railIndexField.setInt(this, index);
        } catch (ReflectiveOperationException ignored) {
            // Rendering can continue without hard-failing.
        }
    }

    @Unique
    private Field crosstie$getRailIndexField() {
        if (CROSSTIE_RAIL_INDEX_FIELD_RESOLVED) {
            return CROSSTIE_RAIL_INDEX_FIELD;
        }

        synchronized (RTMRailPartsRenderSafeMixin.class) {
            if (CROSSTIE_RAIL_INDEX_FIELD_RESOLVED) {
                return CROSSTIE_RAIL_INDEX_FIELD;
            }

            for (String fieldName : CROSSTIE_RAIL_INDEX_FIELD_CANDIDATES) {
                try {
                    Field currentRailIndex = this.getClass().getDeclaredField(fieldName);
                    if (currentRailIndex.getType() != int.class) {
                        continue;
                    }
                    currentRailIndex.setAccessible(true);
                    CROSSTIE_RAIL_INDEX_FIELD = currentRailIndex;
                    break;
                } catch (ReflectiveOperationException ignored) {
                    // Ignore and continue with fallback candidates.
                }
            }

            CROSSTIE_RAIL_INDEX_FIELD_RESOLVED = true;
            return CROSSTIE_RAIL_INDEX_FIELD;
        }
    }

    @Unique
    private void crosstie$invokeRailRenderer(String methodName, TileEntity tileEntity, double x, double y, double z,
            float par8) {
        try {
            Method method = this.crosstie$getRailRendererMethod(methodName);
            method.invoke(this, tileEntity, x, y, z, par8);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to invoke " + methodName, e);
        }
    }

    @Unique
    private Method crosstie$getRailRendererMethod(String methodName) throws ReflectiveOperationException {
        Method cached = CROSSTIE_RENDER_METHOD_CACHE.get(methodName);
        if (cached != null) {
            return cached;
        }

        Class<?> targetClass = this.crosstie$getRailCoreClass();
        Method method = this.getClass().getDeclaredMethod(methodName, targetClass, double.class, double.class,
                double.class, float.class);
        method.setAccessible(true);
        CROSSTIE_RENDER_METHOD_CACHE.put(methodName, method);
        return method;
    }

    @Unique
    private Class<?> crosstie$getRailCoreClass() throws ClassNotFoundException {
        Class<?> cached = CROSSTIE_RAIL_CORE_CLASS;
        if (cached != null) {
            return cached;
        }

        Class<?> loaded = Class.forName(TARGET_CLASS_NAME);
        CROSSTIE_RAIL_CORE_CLASS = loaded;
        return loaded;
    }

    @Unique
    private String crosstie$getRailModel(TileEntity tileEntity) {
        try {
            Method getProperty = this.crosstie$findMethod(tileEntity.getClass(), "getProperty");
            Object property = getProperty.invoke(tileEntity);
            if (property == null) {
                return null;
            }

            Field railModelField = this.crosstie$findField(property.getClass(), "railModel");
            Object railModel = railModelField.get(property);
            return railModel instanceof String ? (String) railModel : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private boolean crosstie$captureGLState() {
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(CROSSTIE_GL_CLIENT_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private void crosstie$restoreGLState(boolean glStateCaptured) {
        if (!glStateCaptured) {
            return;
        }

        try {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
        } catch (RuntimeException ignored) {
            // Keep rendering alive even if a broken script left the GL stack inconsistent.
        } finally {
            // Reset commonly leaked state so chunk/shader rendering does not inherit rail script values.
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }
}
