package net.suzumiya.crosstie.mixins.rtm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    @Unique
    private static final String[] CROSSTIE_START_RP_ACCESSORS = { "getStartRP", "startRP", "getStartRailPos",
            "getStartRailPosition" };
    @Unique
    private static final String[] CROSSTIE_END_RP_ACCESSORS = { "getEndRP", "endRP", "getEndRailPos",
            "getEndRailPosition" };
    @Unique
    private static final String[][] CROSSTIE_RAIL_POS_COORD_NAMES = {
            { "blockX", "x", "posX" },
            { "blockY", "y", "posY" },
            { "blockZ", "z", "posZ" }
    };
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
            List<Object> maps = this.crosstie$collectRailMaps(mapsObj);
            if (maps.isEmpty()) {
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
                Object start = this.crosstie$readAccessorValue(map, CROSSTIE_START_RP_ACCESSORS);
                Object end = this.crosstie$readAccessorValue(map, CROSSTIE_END_RP_ACCESSORS);
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
        Integer x = this.crosstie$readRailPosCoord(railPos, 0);
        Integer y = this.crosstie$readRailPosCoord(railPos, 1);
        Integer z = this.crosstie$readRailPosCoord(railPos, 2);
        if (x == null || y == null || z == null) {
            return false;
        }

        holder[0] = Math.min(holder[0], x.intValue());
        holder[1] = Math.min(holder[1], y.intValue());
        holder[2] = Math.min(holder[2], z.intValue());
        holder[3] = Math.max(holder[3], x.intValue());
        holder[4] = Math.max(holder[4], y.intValue());
        holder[5] = Math.max(holder[5], z.intValue());
        return true;
    }

    @Unique
    private List<Object> crosstie$collectRailMaps(Object mapsObj) {
        if (mapsObj == null) {
            return Collections.emptyList();
        }

        if (mapsObj instanceof Collection<?>) {
            return new ArrayList<Object>((Collection<?>) mapsObj);
        }
        if (mapsObj instanceof Iterable<?>) {
            List<Object> list = new ArrayList<Object>();
            for (Object map : (Iterable<?>) mapsObj) {
                list.add(map);
            }
            return list;
        }
        if (mapsObj.getClass().isArray()) {
            int length = Array.getLength(mapsObj);
            List<Object> list = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(mapsObj, i));
            }
            return list;
        }

        return Collections.emptyList();
    }

    @Unique
    private Object crosstie$readAccessorValue(Object target, String[] accessorNames) {
        for (String accessorName : accessorNames) {
            try {
                Method method = this.crosstie$findMethod(target.getClass(), accessorName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor candidate.
            }

            try {
                Field field = this.crosstie$findField(target.getClass(), accessorName);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor candidate.
            }
        }

        return null;
    }

    @Unique
    private Integer crosstie$readRailPosCoord(Object railPos, int axisIndex) {
        String[] fieldNames = CROSSTIE_RAIL_POS_COORD_NAMES[axisIndex];
        for (String fieldName : fieldNames) {
            try {
                Field field = this.crosstie$findField(railPos.getClass(), fieldName);
                Object value = field.get(railPos);
                Integer coord = this.crosstie$toCoord(value);
                if (coord != null) {
                    return coord;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }

        String[] methodNames = {
                "get" + Character.toUpperCase(fieldNames[0].charAt(0)) + fieldNames[0].substring(1),
                "get" + Character.toUpperCase(fieldNames[1].charAt(0)) + fieldNames[1].substring(1),
                "get" + Character.toUpperCase(fieldNames[2].charAt(0)) + fieldNames[2].substring(1)
        };
        for (String methodName : methodNames) {
            try {
                Method method = this.crosstie$findMethod(railPos.getClass(), methodName);
                Integer coord = this.crosstie$toCoord(method.invoke(railPos));
                if (coord != null) {
                    return coord;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }

        return null;
    }

    @Unique
    private Integer crosstie$toCoord(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf((int) Math.floor(((Number) value).doubleValue()));
        }
        return null;
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
