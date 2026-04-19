package net.suzumiya.crosstie.util;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable snapshot cache for ATSAssist IFTTT results.
 * CrossTie only consumes the post-update state hash and never affects logic timing/order.
 */
public final class IFTTTRenderSnapshotCache {

    private static final List<String> PRIORITY_FIELDS = Arrays.asList(
            "signalState",
            "currentSignal",
            "currentAspect",
            "aspect",
            "displayState",
            "state",
            "mode",
            "output",
            "red",
            "yellow",
            "green",
            "isRed",
            "isYellow",
            "isGreen",
            "power",
            "powered");

    private static final AtomicLong VERSION_COUNTER = new AtomicLong(1L);
    private static final Map<Long, Long> SNAPSHOT_HASH = new ConcurrentHashMap<Long, Long>();
    private static final Map<Long, Long> SNAPSHOT_VERSION = new ConcurrentHashMap<Long, Long>();
    private static final Map<Class<?>, Field[]> CLASS_FIELD_CACHE = new ConcurrentHashMap<Class<?>, Field[]>();

    private IFTTTRenderSnapshotCache() {
    }

    public static long updateFromTile(TileEntity tile) {
        if (tile == null || tile.getWorldObj() == null) {
            return 0L;
        }

        long key = toKey(tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
        long hash = computeHash(tile);
        Long previous = SNAPSHOT_HASH.put(key, Long.valueOf(hash));
        if (previous == null || previous.longValue() != hash) {
            long version = VERSION_COUNTER.incrementAndGet();
            SNAPSHOT_VERSION.put(key, Long.valueOf(version));
            return version;
        }

        Long stableVersion = SNAPSHOT_VERSION.get(key);
        return stableVersion == null ? 0L : stableVersion.longValue();
    }

    public static long getVersion(World world, int x, int y, int z) {
        Long version = SNAPSHOT_VERSION.get(toKey(world, x, y, z));
        return version == null ? 0L : version.longValue();
    }

    public static boolean isDirtySince(World world, int x, int y, int z, long lastVersion) {
        long version = getVersion(world, x, y, z);
        return version > 0L && version != lastVersion;
    }

    private static long computeHash(TileEntity tile) {
        long result = 1125899906842597L;
        Field[] fields = getTrackedFields(tile.getClass());
        for (Field field : fields) {
            Object value = null;
            if (field != null) {
                try {
                    value = field.get(tile);
                } catch (IllegalAccessException ignored) {
                    value = null;
                }
            }
            result = 31L * result + deepHash(value);
        }

        return result;
    }

    private static Field[] getTrackedFields(Class<?> type) {
        Field[] cached = CLASS_FIELD_CACHE.get(type);
        if (cached != null) {
            return cached;
        }

        Field[] resolved = new Field[PRIORITY_FIELDS.size()];
        for (int i = 0; i < PRIORITY_FIELDS.size(); i++) {
            resolved[i] = resolveField(type, PRIORITY_FIELDS.get(i));
        }
        CLASS_FIELD_CACHE.put(type, resolved);
        return resolved;
    }

    private static Field resolveField(Class<?> start, String fieldName) {
        Class<?> cursor = start;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private static long deepHash(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value.getClass().isArray()) {
            if (value instanceof Object[]) {
                return Arrays.deepHashCode((Object[]) value);
            }
            if (value instanceof int[]) {
                return Arrays.hashCode((int[]) value);
            }
            if (value instanceof long[]) {
                return Arrays.hashCode((long[]) value);
            }
            if (value instanceof byte[]) {
                return Arrays.hashCode((byte[]) value);
            }
            if (value instanceof short[]) {
                return Arrays.hashCode((short[]) value);
            }
            if (value instanceof char[]) {
                return Arrays.hashCode((char[]) value);
            }
            if (value instanceof boolean[]) {
                return Arrays.hashCode((boolean[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.hashCode((float[]) value);
            }
            if (value instanceof double[]) {
                return Arrays.hashCode((double[]) value);
            }
        }
        return value.hashCode();
    }

    private static long toKey(World world, int x, int y, int z) {
        int dim = world == null || world.provider == null ? 0 : world.provider.dimensionId;
        long hash = ((long) dim & 0xFFFFL) << 48;
        hash ^= ((long) (x & 0x3FFFFFF)) << 22;
        hash ^= ((long) (z & 0x3FFFFFF));
        hash ^= ((long) (y & 0xFFFL)) << 10;
        return hash;
    }
}
