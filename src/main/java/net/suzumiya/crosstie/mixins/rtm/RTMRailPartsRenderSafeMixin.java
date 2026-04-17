package net.suzumiya.crosstie.mixins.rtm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
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
    private static final int CROSSTIE_AABB_CULL_MARGIN_CHUNKS = 2;

    @Inject(method = "renderRail(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;IDDDF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderRailSafely(@Coerce Object tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        TileEntity railTile = (TileEntity) tileEntity;
        if (this.crosstie$shouldCullRail(railTile)) {
            ci.cancel();
            return;
        }

        boolean glStateCaptured = false;
        try {
            glStateCaptured = this.crosstie$captureGLState();
            this.crosstie$setCurrentRailIndex(index);
            if (this.crosstie$isHi03Rail(railTile)) {
                Hi03ExpressRailwayContext.enter();
            }

            this.crosstie$invokeRailRenderer("renderRailStatic", railTile, x, y, z, par8);
            this.crosstie$invokeRailRenderer("renderRailDynamic", railTile, x, y, z, par8);
        } catch (Exception e) {
            throw new RuntimeException("On init script", e);
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

        ci.cancel();
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

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        double cullDist = renderChunks * 16.0D;
        double cullDistSq = cullDist * cullDist;
        double px = mc.renderViewEntity.posX;
        double py = mc.renderViewEntity.posY;
        double pz = mc.renderViewEntity.posZ;

        AxisAlignedBB railAabb = this.crosstie$getEffectiveRailAabb(tileEntity);
        if (railAabb != null) {
            double aabbCullDist = (renderChunks + CROSSTIE_AABB_CULL_MARGIN_CHUNKS) * 16.0D;
            double aabbCullDistSq = aabbCullDist * aabbCullDist;
            return this.crosstie$distanceSqToAabb(px, py, pz, railAabb) > aabbCullDistSq;
        }

        return tileEntity.getDistanceFrom(px, py, pz) > cullDistSq;
    }

    @Unique
    private double crosstie$distanceSqToAabb(double x, double y, double z, AxisAlignedBB aabb) {
        double dx = 0.0D;
        if (x < aabb.minX) {
            dx = aabb.minX - x;
        } else if (x > aabb.maxX) {
            dx = x - aabb.maxX;
        }

        double dy = 0.0D;
        if (y < aabb.minY) {
            dy = aabb.minY - y;
        } else if (y > aabb.maxY) {
            dy = y - aabb.maxY;
        }

        double dz = 0.0D;
        if (z < aabb.minZ) {
            dz = aabb.minZ - z;
        } else if (z > aabb.maxZ) {
            dz = z - aabb.maxZ;
        }

        return dx * dx + dy * dy + dz * dz;
    }

    @Unique
    private AxisAlignedBB crosstie$getEffectiveRailAabb(TileEntity tileEntity) {
        AxisAlignedBB baseAabb = tileEntity.getRenderBoundingBox();
        AxisAlignedBB mapAabb = this.crosstie$buildRailMapAabb(tileEntity);
        if (baseAabb == null) {
            return mapAabb;
        }
        if (mapAabb == null) {
            return baseAabb;
        }
        return baseAabb.func_111270_a(mapAabb);
    }

    @Unique
    private AxisAlignedBB crosstie$buildRailMapAabb(TileEntity tileEntity) {
        try {
            Method getAllRailMaps = this.crosstie$findMethod(tileEntity.getClass(), "getAllRailMaps");
            Object mapsObj = getAllRailMaps.invoke(tileEntity);
            if (!(mapsObj instanceof Object[])) {
                return null;
            }

            Object[] maps = (Object[]) mapsObj;
            if (maps.length == 0) {
                return null;
            }

            int[] holder = {
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
            };
            boolean hasPoint = false;
            for (Object map : maps) {
                if (map == null) {
                    continue;
                }
                Method getStartRP = this.crosstie$findMethod(map.getClass(), "getStartRP");
                Method getEndRP = this.crosstie$findMethod(map.getClass(), "getEndRP");
                Object start = getStartRP.invoke(map);
                Object end = getEndRP.invoke(map);
                hasPoint |= this.crosstie$accumulateRailPos(start, holder);
                hasPoint |= this.crosstie$accumulateRailPos(end, holder);
            }
            if (!hasPoint) {
                return null;
            }

            return AxisAlignedBB.getBoundingBox(holder[0] - 3.5D, holder[1] - 10.0D, holder[2] - 3.5D,
                    holder[3] + 5.5D, holder[4] + 2.0D, holder[5] + 5.5D);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private boolean crosstie$accumulateRailPos(Object railPos, int[] holder) {
        if (railPos == null) {
            return false;
        }
        try {
            Field xField = this.crosstie$findField(railPos.getClass(), "blockX");
            Field yField = this.crosstie$findField(railPos.getClass(), "blockY");
            Field zField = this.crosstie$findField(railPos.getClass(), "blockZ");
            int x = xField.getInt(railPos);
            int y = yField.getInt(railPos);
            int z = zField.getInt(railPos);
            holder[0] = Math.min(holder[0], x);
            holder[1] = Math.min(holder[1], y);
            holder[2] = Math.min(holder[2], z);
            holder[3] = Math.max(holder[3], x);
            holder[4] = Math.max(holder[4], y);
            holder[5] = Math.max(holder[5], z);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Unique
    private Method crosstie$findMethod(Class<?> owner, String name) throws NoSuchMethodException {
        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Unique
    private Field crosstie$findField(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Unique
    private void crosstie$setCurrentRailIndex(int index) {
        for (String fieldName : CROSSTIE_RAIL_INDEX_FIELD_CANDIDATES) {
            try {
                Field currentRailIndex = this.getClass().getDeclaredField(fieldName);
                if (currentRailIndex.getType() != int.class) {
                    continue;
                }
                currentRailIndex.setAccessible(true);
                currentRailIndex.setInt(this, index);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Ignore and continue with fallback candidates.
            }
        }

        // KaizPatchX variants may not expose a writable index field.
        // Rendering can continue without hard-failing.
    }

    @Unique
    private void crosstie$invokeRailRenderer(String methodName, TileEntity tileEntity, double x, double y, double z,
            float par8) {
        try {
            Class<?> targetClass = Class.forName(TARGET_CLASS_NAME);
            Method method = this.getClass().getDeclaredMethod(methodName, targetClass, double.class, double.class,
                    double.class, float.class);
            method.setAccessible(true);
            method.invoke(this, tileEntity, x, y, z, par8);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to invoke " + methodName, e);
        }
    }

    @Unique
    private String crosstie$getRailModel(TileEntity tileEntity) {
        try {
            Method getProperty = tileEntity.getClass().getMethod("getProperty");
            Object property = getProperty.invoke(tileEntity);
            if (property == null) {
                return null;
            }

            Field railModelField = property.getClass().getField("railModel");
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
