package net.suzumiya.crosstie.util;

import net.minecraft.util.AxisAlignedBB;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared rail bounds resolver used by RTM/NGT mixins.
 * Consolidates duplicated reflection and coordinate extraction logic.
 */
public final class RailAabbResolver {

    private static final String[] START_RP_ACCESSORS = { "getStartRP", "startRP", "getStartRailPos", "getStartRailPosition" };
    private static final String[] END_RP_ACCESSORS = { "getEndRP", "endRP", "getEndRailPos", "getEndRailPosition" };
    private static final String[][] RAIL_POS_COORD_NAMES = {
            { "blockX", "x", "posX" },
            { "blockY", "y", "posY" },
            { "blockZ", "z", "posZ" }
    };

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();

    private RailAabbResolver() {
    }

    public static AxisAlignedBB getEffectiveRailAabb(Object railHolder, AxisAlignedBB baseAabb) {
        AxisAlignedBB mapAabb = buildRailMapAabb(railHolder);
        if (baseAabb == null) {
            return mapAabb;
        }
        if (mapAabb == null) {
            return baseAabb;
        }
        return baseAabb.func_111270_a(mapAabb);
    }

    public static double distanceSqToAabb(double x, double y, double z, AxisAlignedBB aabb) {
        if (aabb == null) {
            return Double.POSITIVE_INFINITY;
        }

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

    private static AxisAlignedBB buildRailMapAabb(Object railHolder) {
        if (railHolder == null) {
            return null;
        }

        try {
            Method getAllRailMaps = findMethod(railHolder.getClass(), "getAllRailMaps");
            Object mapsObj = getAllRailMaps.invoke(railHolder);
            if (mapsObj == null) {
                return null;
            }

            int[] holder = {
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
            };
            boolean hasPoint = accumulateFromMaps(mapsObj, holder);
            if (!hasPoint) {
                return null;
            }

            return AxisAlignedBB.getBoundingBox(holder[0] - 3.5D, holder[1] - 10.0D, holder[2] - 3.5D,
                    holder[3] + 5.5D, holder[4] + 2.0D, holder[5] + 5.5D);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean accumulateFromMaps(Object mapsObj, int[] holder) {
        boolean hasPoint = false;

        if (mapsObj instanceof Iterable<?>) {
            for (Object map : (Iterable<?>) mapsObj) {
                hasPoint |= accumulateFromMap(map, holder);
            }
            return hasPoint;
        }

        if (mapsObj.getClass().isArray()) {
            int length = Array.getLength(mapsObj);
            for (int i = 0; i < length; i++) {
                hasPoint |= accumulateFromMap(Array.get(mapsObj, i), holder);
            }
            return hasPoint;
        }

        return false;
    }

    private static boolean accumulateFromMap(Object map, int[] holder) {
        if (map == null) {
            return false;
        }

        boolean hasPoint = false;
        Object start = readAccessorValue(map, START_RP_ACCESSORS);
        Object end = readAccessorValue(map, END_RP_ACCESSORS);
        hasPoint |= accumulateRailPos(start, holder);
        hasPoint |= accumulateRailPos(end, holder);
        return hasPoint;
    }

    private static boolean accumulateRailPos(Object railPos, int[] holder) {
        if (railPos == null) {
            return false;
        }

        Integer x = readRailPosCoord(railPos, 0);
        Integer y = readRailPosCoord(railPos, 1);
        Integer z = readRailPosCoord(railPos, 2);
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

    private static Object readAccessorValue(Object target, String[] accessorNames) {
        for (String accessorName : accessorNames) {
            try {
                Method method = findMethod(target.getClass(), accessorName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }

            try {
                Field field = findField(target.getClass(), accessorName);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }

        return null;
    }

    private static Integer readRailPosCoord(Object railPos, int axisIndex) {
        String[] fieldNames = RAIL_POS_COORD_NAMES[axisIndex];

        for (String fieldName : fieldNames) {
            try {
                Field field = findField(railPos.getClass(), fieldName);
                Integer coord = toCoord(field.get(railPos));
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
                Method method = findMethod(railPos.getClass(), methodName);
                Integer coord = toCoord(method.invoke(railPos));
                if (coord != null) {
                    return coord;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }

        return null;
    }

    private static Integer toCoord(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf((int) Math.floor(((Number) value).doubleValue()));
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String name) throws NoSuchMethodException {
        String cacheKey = owner.getName() + "#" + name;
        Method cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                METHOD_CACHE.put(cacheKey, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        String cacheKey = owner.getName() + "#" + name;
        Field cached = FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(cacheKey, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
