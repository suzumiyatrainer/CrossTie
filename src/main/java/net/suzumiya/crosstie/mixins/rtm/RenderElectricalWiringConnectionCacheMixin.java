package net.suzumiya.crosstie.mixins.rtm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Avoids RenderElectricalWiring's per-frame loadedEntityList scan for TO_ENTITY wires.
 *
 * <p>Connection already exposes a cached electrical-wiring lookup path, so this mixin
 * reuses that for WIRE/TO_ENTITY while preserving the original coordinate math.
 *
 * <p><b>リフレクションキャッシュ</b>: 以前は {@code Field} / {@code Method} の解決を
 * フレームごとに行っていた。現在は初回のみ解決して static フィールドにキャッシュするため、
 * 配線が多い環境でのリフレクションオーバーヘッドが大幅に削減される。
 */
@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class RenderElectricalWiringConnectionCacheMixin {

    // ---- リフレクションキャッシュ（初回ロード後は再解決しない） ----

    /** Connection.type フィールド */
    private static volatile Field crosstie$connectionTypeField;

    /** Connection.getElectricalWiring(World) メソッド */
    private static volatile Method crosstie$getElectricalWiringMethod;

    /** getWirePos() メソッド */
    private static volatile Method crosstie$getWirePosMethod;

    /** Vec3.getX() (またはX() / x プロパティ) に相当するメソッド */
    private static volatile Method crosstie$getXMethod;

    /** Vec3.getY() */
    private static volatile Method crosstie$getYMethod;

    /** Vec3.getZ() */
    private static volatile Method crosstie$getZMethod;

    /** PooledVec3.create(double, double, double) または (float, float, float) */
    private static volatile Method crosstie$pooledVec3CreateMethod;

    // ---- isDummyElectricalWiring 判定キャッシュ ----
    private static final String DUMMY_EW_CLASS = "jp.ngt.rtm.electric.TileEntityDummyEW";

    @Inject(method = "getConnectedTarget", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$useCachedConnectedTarget(
            @Coerce Object tileEntity,
            @Coerce Object connection,
            float partialTicks,
            CallbackInfoReturnable<Object> cir) {
        if (!CrossTieConfig.connectionCacheEnabled) {
            return;
        }
        if (!(tileEntity instanceof TileEntity) || connection == null) {
            return;
        }

        TileEntity sourceTile = (TileEntity) tileEntity;
        if (sourceTile.getWorldObj() == null) {
            return;
        }

        String typeName = crosstie$getConnectionTypeName(connection);
        if (!"WIRE".equals(typeName) && !"TO_ENTITY".equals(typeName)) {
            return;
        }

        Object target = crosstie$invokeGetElectricalWiring(connection, sourceTile.getWorldObj());
        if (!(target instanceof TileEntity) || ((TileEntity) target).isInvalid()) {
            return;
        }

        TileEntity targetTile = (TileEntity) target;
        Object posMain = crosstie$invokeGetWirePos(tileEntity);
        Object posTarget = crosstie$invokeGetWirePos(target);
        if (posMain == null || posTarget == null) {
            return;
        }

        double thisX = sourceTile.xCoord + 0.5D + crosstie$getVecComponent(posMain, 0);
        double thisY = sourceTile.yCoord
                + (crosstie$isDummyElectricalWiring(sourceTile) ? 0.0D : 0.5D)
                + crosstie$getVecComponent(posMain, 1);
        double thisZ = sourceTile.zCoord + 0.5D + crosstie$getVecComponent(posMain, 2);
        double targetYOffset = crosstie$isDummyElectricalWiring(targetTile) ? 0.0D : 0.5D;

        double x = targetTile.xCoord + 0.5D + crosstie$getVecComponent(posTarget, 0) - thisX;
        double y = targetTile.yCoord + targetYOffset + crosstie$getVecComponent(posTarget, 1) - thisY;
        double z = targetTile.zCoord + 0.5D + crosstie$getVecComponent(posTarget, 2) - thisZ;
        Object pooledVec = crosstie$createPooledVec3(x, y, z);
        if (pooledVec != null) {
            cir.setReturnValue(pooledVec);
        }
    }

    // ---- キャッシュ付きリフレクションヘルパー ----

    private static String crosstie$getConnectionTypeName(Object connection) {
        try {
            Field f = crosstie$connectionTypeField;
            if (f == null) {
                f = crosstie$findField(connection.getClass(), "type");
                crosstie$connectionTypeField = f;
            }
            if (f == null) return "";
            Object type = f.get(connection);
            return type instanceof Enum ? ((Enum<?>) type).name() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static Object crosstie$invokeGetElectricalWiring(Object connection, Object world) {
        try {
            Method m = crosstie$getElectricalWiringMethod;
            if (m == null) {
                m = crosstie$findMethod(connection.getClass(), "getElectricalWiring", 1);
                crosstie$getElectricalWiringMethod = m;
            }
            if (m == null) return null;
            return m.invoke(connection, world);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object crosstie$invokeGetWirePos(Object tileOrTarget) {
        try {
            Method m = crosstie$getWirePosMethod;
            if (m == null) {
                m = crosstie$findMethod(tileOrTarget.getClass(), "getWirePos", 0);
                crosstie$getWirePosMethod = m;
            }
            if (m == null) return null;
            return m.invoke(tileOrTarget);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * ベクトルオブジェクトから X/Y/Z 成分を取得する。
     * @param axis 0=X, 1=Y, 2=Z
     */
    private static double crosstie$getVecComponent(Object vec, int axis) {
        try {
            final String[] names = {"getX", "getY", "getZ"};
            Method m;
            switch (axis) {
                case 0:
                    m = crosstie$getXMethod;
                    if (m == null) { m = crosstie$findMethod(vec.getClass(), names[0], 0); crosstie$getXMethod = m; }
                    break;
                case 1:
                    m = crosstie$getYMethod;
                    if (m == null) { m = crosstie$findMethod(vec.getClass(), names[1], 0); crosstie$getYMethod = m; }
                    break;
                default:
                    m = crosstie$getZMethod;
                    if (m == null) { m = crosstie$findMethod(vec.getClass(), names[2], 0); crosstie$getZMethod = m; }
                    break;
            }
            if (m == null) return 0.0D;
            Object value = m.invoke(vec);
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
        } catch (Throwable t) {
            return 0.0D;
        }
    }

    private static Object crosstie$createPooledVec3(double x, double y, double z) {
        try {
            Method m = crosstie$pooledVec3CreateMethod;
            if (m == null) {
                Class<?> pooledVec3 = Class.forName("jp.ngt.ngtlib.math.PooledVec3");
                for (Method method : pooledVec3.getMethods()) {
                    if ("create".equals(method.getName()) && method.getParameterTypes().length == 3) {
                        m = method;
                        break;
                    }
                }
                crosstie$pooledVec3CreateMethod = m;
            }
            if (m == null) return null;
            Class<?>[] paramTypes = m.getParameterTypes();
            return m.invoke(null,
                    crosstie$coerceNumber(paramTypes[0], x),
                    crosstie$coerceNumber(paramTypes[1], y),
                    crosstie$coerceNumber(paramTypes[2], z));
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean crosstie$isDummyElectricalWiring(Object tile) {
        return tile != null && DUMMY_EW_CLASS.equals(tile.getClass().getName());
    }

    // ---- 汎用リフレクションユーティリティ ----

    /**
     * クラス階層を辿って指定名のフィールドを探し、{@code accessible} にして返す。
     */
    private static Field crosstie$findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        return null;
    }

    /**
     * クラス階層を辿って指定名・引数数のメソッドを探し、{@code accessible} にして返す。
     */
    private static Method crosstie$findMethod(Class<?> type, String name, int paramCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == paramCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object crosstie$coerceNumber(Class<?> parameterType, double value) {
        if (parameterType == float.class || parameterType == Float.class) {
            return (float) value;
        }
        if (parameterType == int.class || parameterType == Integer.class) {
            return (int) value;
        }
        if (parameterType == long.class || parameterType == Long.class) {
            return (long) value;
        }
        return value;
    }
}
