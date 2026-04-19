package net.suzumiya.crosstie.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast culling + LOD gate for RTM electrical wire rendering.
 */
public final class WireRenderOptimizer {

    private static final double TILE_MARGIN_CHUNKS = 1.5D;
    private static final double WIRE_MARGIN_CHUNKS = 2.5D;
    private static final double BACK_CULL_DOT = -0.25D;

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context();
        }
    };

    private static final String[][] VEC3_FIELD_CANDIDATES = {
            { "xCoord", "x", "X", "xPos" },
            { "yCoord", "y", "Y", "yPos" },
            { "zCoord", "z", "Z", "zPos" }
    };

    private static final String[][] VEC3_METHOD_CANDIDATES = {
            { "getX", "x" },
            { "getY", "y" },
            { "getZ", "z" }
    };

    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();

    private WireRenderOptimizer() {
    }

    public static void beginTileRender(TileEntity tile) {
        Context ctx = CONTEXT.get();
        ctx.tile = tile;
        ctx.tileSkip = false;

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc == null ? null : mc.renderViewEntity;
        if (tile == null || viewer == null) {
            ctx.ready = false;
            return;
        }

        int renderChunks = Math.max(4, mc.gameSettings.renderDistanceChunks);
        double tileRange = (renderChunks + TILE_MARGIN_CHUNKS) * 16.0D;
        double wireRange = (renderChunks + WIRE_MARGIN_CHUNKS) * 16.0D;

        ctx.ready = true;
        ctx.viewerX = viewer.posX;
        ctx.viewerY = viewer.posY;
        ctx.viewerZ = viewer.posZ;
        ctx.lookX = viewer.getLookVec().xCoord;
        ctx.lookY = viewer.getLookVec().yCoord;
        ctx.lookZ = viewer.getLookVec().zCoord;
        ctx.tileCullSq = tileRange * tileRange;
        ctx.wireCullSq = wireRange * wireRange;
        ctx.lodStartSq = (renderChunks * 16.0D) * (renderChunks * 16.0D);

        double cx = tile.xCoord + 0.5D;
        double cy = tile.yCoord + 0.5D;
        double cz = tile.zCoord + 0.5D;
        ctx.tileSkip = distSq(ctx.viewerX, ctx.viewerY, ctx.viewerZ, cx, cy, cz) > ctx.tileCullSq;
    }

    public static void endTileRender() {
        Context ctx = CONTEXT.get();
        ctx.ready = false;
        ctx.tile = null;
        ctx.tileSkip = false;
    }

    public static boolean shouldSkipTile(TileEntity tile) {
        Context ctx = CONTEXT.get();
        if (!ctx.ready || tile == null) {
            beginTileRender(tile);
            ctx = CONTEXT.get();
            if (!ctx.ready) {
                return false;
            }
        }

        if (ctx.tile == tile) {
            return ctx.tileSkip;
        }

        double cx = tile.xCoord + 0.5D;
        double cy = tile.yCoord + 0.5D;
        double cz = tile.zCoord + 0.5D;
        return distSq(ctx.viewerX, ctx.viewerY, ctx.viewerZ, cx, cy, cz) > ctx.tileCullSq;
    }

    public static boolean shouldSkipWire(TileEntity tile, Object targetVec3) {
        if (shouldSkipTile(tile)) {
            return true;
        }

        Context ctx = CONTEXT.get();
        if (!ctx.ready || tile == null || targetVec3 == null) {
            return false;
        }

        double tx = tile.xCoord + 0.5D;
        double ty = tile.yCoord + 0.5D;
        double tz = tile.zCoord + 0.5D;

        double ex = readVecCoord(targetVec3, 0, tx);
        double ey = readVecCoord(targetVec3, 1, ty);
        double ez = readVecCoord(targetVec3, 2, tz);

        double dTileSq = distSq(ctx.viewerX, ctx.viewerY, ctx.viewerZ, tx, ty, tz);
        double dEndSq = distSq(ctx.viewerX, ctx.viewerY, ctx.viewerZ, ex, ey, ez);
        if (Math.min(dTileSq, dEndSq) > ctx.wireCullSq) {
            return true;
        }

        double mx = (tx + ex) * 0.5D;
        double my = (ty + ey) * 0.5D;
        double mz = (tz + ez) * 0.5D;
        double vx = mx - ctx.viewerX;
        double vy = my - ctx.viewerY;
        double vz = mz - ctx.viewerZ;
        double dot = vx * ctx.lookX + vy * ctx.lookY + vz * ctx.lookZ;
        double midSq = vx * vx + vy * vy + vz * vz;

        if (dot < 0.0D && dot * dot > midSq * BACK_CULL_DOT * BACK_CULL_DOT && midSq > ctx.lodStartSq) {
            return true;
        }

        if (midSq > ctx.lodStartSq) {
            int keepMask = midSq > ctx.lodStartSq * 4.0D ? 3 : 1;
            int h = hashWire(tile.xCoord, tile.yCoord, tile.zCoord, ex, ey, ez);
            if ((h & keepMask) != 0) {
                return true;
            }
        }

        return false;
    }

    private static int hashWire(int x, int y, int z, double ex, double ey, double ez) {
        int hx = (int) Math.floor(ex * 2.0D);
        int hy = (int) Math.floor(ey * 2.0D);
        int hz = (int) Math.floor(ez * 2.0D);
        int h = x * 73428767 ^ y * 912931 ^ z * 19349663;
        h ^= hx * 83492791;
        h ^= hy * 297657976;
        h ^= hz * 42317861;
        return h;
    }

    private static double distSq(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double readVecCoord(Object vec, int axis, double fallback) {
        Double fromField = readFieldCoord(vec, axis);
        if (fromField != null) {
            return fromField.doubleValue();
        }

        Double fromMethod = readMethodCoord(vec, axis);
        if (fromMethod != null) {
            return fromMethod.doubleValue();
        }

        return fallback;
    }

    private static Double readFieldCoord(Object vec, int axis) {
        String[] names = VEC3_FIELD_CANDIDATES[axis];
        Class<?> owner = vec.getClass();
        for (String name : names) {
            try {
                Field field = findField(owner, name);
                Object value = field.get(vec);
                if (value instanceof Number) {
                    return Double.valueOf(((Number) value).doubleValue());
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }
        return null;
    }

    private static Double readMethodCoord(Object vec, int axis) {
        String[] names = VEC3_METHOD_CANDIDATES[axis];
        Class<?> owner = vec.getClass();
        for (String name : names) {
            try {
                Method method = findMethod(owner, name);
                Object value = method.invoke(vec);
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

        Class<?> c = owner;
        while (c != null) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> owner, String name) throws NoSuchMethodException {
        String key = owner.getName() + "#" + name;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Class<?> c = owner;
        while (c != null) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }

        throw new NoSuchMethodException(name);
    }

    private static final class Context {
        private boolean ready;
        private TileEntity tile;
        private boolean tileSkip;
        private double viewerX;
        private double viewerY;
        private double viewerZ;
        private double lookX;
        private double lookY;
        private double lookZ;
        private double tileCullSq;
        private double wireCullSq;
        private double lodStartSq;
    }
}
