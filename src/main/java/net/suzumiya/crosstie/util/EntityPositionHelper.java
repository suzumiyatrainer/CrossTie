package net.suzumiya.crosstie.util;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads entity coordinates without relying on MCP field names inside remap=false mixins.
 */
public final class EntityPositionHelper {

    private static final String[][] FIELD_CANDIDATES = {
            { "posX", "field_70165_t" },
            { "posY", "field_70163_u" },
            { "posZ", "field_70161_v" }
    };

    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();

    private EntityPositionHelper() {
    }

    public static boolean tryGetPosition(Object entity, double[] out) {
        if (entity == null || out == null || out.length < 3) {
            return false;
        }

        Double x = readCoord(entity, 0);
        Double y = readCoord(entity, 1);
        Double z = readCoord(entity, 2);
        if (x == null || y == null || z == null) {
            return false;
        }

        out[0] = x.doubleValue();
        out[1] = y.doubleValue();
        out[2] = z.doubleValue();
        return true;
    }

    private static Double readCoord(Object entity, int axis) {
        Class<?> owner = entity.getClass();
        for (String fieldName : FIELD_CANDIDATES[axis]) {
            try {
                Field field = findField(owner, fieldName);
                Object value = field.get(entity);
                if (value instanceof Number) {
                    return Double.valueOf(((Number) value).doubleValue());
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }
        return null;
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        String key = owner.getName() + "#" + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Class<?> cursor = owner;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }
}
