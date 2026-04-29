package net.suzumiya.crosstie.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public final class GtnhLibIconCompat {

    private static Object modelIsbrhInstance;
    private static Method getParticleIconMethod;
    private static boolean particleIconTakesBlock;
    private static boolean lookedUpModelIsbrh;

    private GtnhLibIconCompat() {
    }

    public static IIcon getParticleIcon(Object block, IBlockAccess blockAccess, int x, int y, int z, int side) {
        IIcon icon = invokeGtnhLibModel(block, blockAccess, x, y, z, side);
        return icon != null ? icon : getFallbackIcon(block, side);
    }

    private static IIcon invokeGtnhLibModel(Object block, IBlockAccess blockAccess, int x, int y, int z, int side) {
        try {
            lookupModelIsbrh();
            if (modelIsbrhInstance == null || getParticleIconMethod == null || !(block instanceof Block)) {
                return null;
            }

            Object model = getModelIsbrhInstance();
            if (model == null) {
                return null;
            }

            Object icon = particleIconTakesBlock
                    ? getParticleIconMethod.invoke(
                            model,
                            block,
                            blockAccess,
                            Integer.valueOf(x),
                            Integer.valueOf(y),
                            Integer.valueOf(z),
                            Integer.valueOf(side))
                    : getParticleIconMethod.invoke(
                            model,
                            blockAccess,
                            Integer.valueOf(x),
                            Integer.valueOf(y),
                            Integer.valueOf(z));
            return icon instanceof IIcon ? (IIcon) icon : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void lookupModelIsbrh() throws ReflectiveOperationException {
        if (lookedUpModelIsbrh) {
            return;
        }
        lookedUpModelIsbrh = true;

        Class<?> modelClass = Class.forName("com.gtnewhorizon.gtnhlib.client.model.ModelISBRH");
        Field instanceField = modelClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        modelIsbrhInstance = instanceField.get(null);

        try {
            getParticleIconMethod = modelClass.getMethod(
                    "getParticleIcon",
                    Block.class,
                    IBlockAccess.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class);
            particleIconTakesBlock = true;
        } catch (NoSuchMethodException ignored) {
            getParticleIconMethod = modelClass.getMethod(
                    "getParticleIcon",
                    IBlockAccess.class,
                    int.class,
                    int.class,
                    int.class);
            particleIconTakesBlock = false;
        }
    }

    public static Object getModelIsbrhInstance() throws ReflectiveOperationException {
        lookupModelIsbrh();
        return modelIsbrhInstance;
    }

    private static IIcon getFallbackIcon(Object block, int side) {
        IIcon icon = getBlockIconField(block);
        if (icon != null) {
            return icon;
        }

        try {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("missingno");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IIcon getBlockIconField(Object block) {
        if (!(block instanceof Block)) {
            return null;
        }

        Class<?> current = block.getClass();
        while (current != null) {
            IIcon icon = getIconField(current, block, "blockIcon");
            if (icon != null) {
                return icon;
            }

            icon = getIconField(current, block, "field_149761_L");
            if (icon != null) {
                return icon;
            }

            current = current.getSuperclass();
        }

        return null;
    }

    private static IIcon getIconField(Class<?> owner, Object block, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(block);
            return value instanceof IIcon ? (IIcon) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
