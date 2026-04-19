package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.rail.TileEntityLargeRailCore", remap = false)
public abstract class TileEntityLargeRailCoreMixin extends TileEntity {

    @Unique
    private static final int CROSSTIE_FORCE_RENDER_CHUNKS = 2;

    @Unique
    private static final double CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS = CROSSTIE_FORCE_RENDER_CHUNKS * 16.0D;
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

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        int renderDistance = CrossTie.proxy.getClientRenderDistance();
        if (renderDistance > 0) {
            double blockDistance = (renderDistance + CROSSTIE_FORCE_RENDER_CHUNKS) * 16.0D;
            return blockDistance * blockDistance;
        }
        return CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS * CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS;
    }

    @Inject(method = "getRenderBoundingBox", at = @At("RETURN"), cancellable = true, remap = false)
    @SideOnly(Side.CLIENT)
    private void crosstie$fixAngelicaRailCulling(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (CrossTieConfig.fixAngelicaRailCulling) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderViewEntity == null) {
            return;
        }

        AxisAlignedBB railAabb = this.crosstie$getEffectiveRailAabb(cir.getReturnValue());
        if (railAabb == null) {
            // Keep rail visible when bounds introspection fails for branch rail variants.
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

        double px = mc.renderViewEntity.posX;
        double py = mc.renderViewEntity.posY;
        double pz = mc.renderViewEntity.posZ;
        AxisAlignedBB playerRange = AxisAlignedBB.getBoundingBox(px, py, pz, px, py, pz)
                .expand(CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS, CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS,
                        CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS);

        if (railAabb.intersectsWith(playerRange)) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

    }

    @Unique
    private AxisAlignedBB crosstie$getEffectiveRailAabb(AxisAlignedBB baseAabb) {
        AxisAlignedBB mapAabb = this.crosstie$buildRailMapAabb();
        if (baseAabb == null) {
            return mapAabb;
        }
        if (mapAabb == null) {
            return baseAabb;
        }
        return baseAabb.func_111270_a(mapAabb);
    }

    @Unique
    private AxisAlignedBB crosstie$buildRailMapAabb() {
        try {
            Method getAllRailMaps = this.crosstie$findMethod(this.getClass(), "getAllRailMaps");
            Object mapsObj = getAllRailMaps.invoke(this);
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
}
