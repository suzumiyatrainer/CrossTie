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
 */
@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class RenderElectricalWiringConnectionCacheMixin {

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

        Object target = crosstie$invoke(connection, "getElectricalWiring", sourceTile.getWorldObj());
        if (!(target instanceof TileEntity) || ((TileEntity) target).isInvalid()) {
            return;
        }

        TileEntity targetTile = (TileEntity) target;
        Object posMain = crosstie$invoke(tileEntity, "getWirePos");
        Object posTarget = crosstie$invoke(target, "getWirePos");
        if (posMain == null || posTarget == null) {
            return;
        }

        double thisX = sourceTile.xCoord + 0.5D + crosstie$getVecComponent(posMain, "getX");
        double thisY = sourceTile.yCoord
                + (crosstie$isDummyElectricalWiring(sourceTile) ? 0.0D : 0.5D)
                + crosstie$getVecComponent(posMain, "getY");
        double thisZ = sourceTile.zCoord + 0.5D + crosstie$getVecComponent(posMain, "getZ");
        double targetYOffset = crosstie$isDummyElectricalWiring(targetTile) ? 0.0D : 0.5D;

        double x = targetTile.xCoord + 0.5D + crosstie$getVecComponent(posTarget, "getX") - thisX;
        double y = targetTile.yCoord + targetYOffset + crosstie$getVecComponent(posTarget, "getY") - thisY;
        double z = targetTile.zCoord + 0.5D + crosstie$getVecComponent(posTarget, "getZ") - thisZ;
        Object pooledVec = crosstie$createPooledVec3(x, y, z);
        if (pooledVec != null) {
            cir.setReturnValue(pooledVec);
        }
    }

    private static String crosstie$getConnectionTypeName(Object connection) {
        Object type = crosstie$getFieldValue(connection, "type");
        return type instanceof Enum ? ((Enum<?>) type).name() : "";
    }

    private static boolean crosstie$isDummyElectricalWiring(Object tile) {
        return tile != null && "jp.ngt.rtm.electric.TileEntityDummyEW".equals(tile.getClass().getName());
    }

    private static double crosstie$getVecComponent(Object vec, String methodName) {
        Object value = crosstie$invoke(vec, methodName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private static Object crosstie$createPooledVec3(double x, double y, double z) {
        try {
            Class<?> pooledVec3 = Class.forName("jp.ngt.ngtlib.math.PooledVec3");
            for (Method method : pooledVec3.getMethods()) {
                if (!"create".equals(method.getName()) || method.getParameterTypes().length != 3) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                return method.invoke(null,
                        crosstie$coerceNumber(parameterTypes[0], x),
                        crosstie$coerceNumber(parameterTypes[1], y),
                        crosstie$coerceNumber(parameterTypes[2], z));
            }
        } catch (ReflectiveOperationException ignored) {
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

    private static Object crosstie$getFieldValue(Object owner, String fieldName) {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(owner);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object crosstie$invoke(Object owner, String methodName, Object... args) {
        Class<?> type = owner.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterTypes().length != args.length) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(owner, args);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
