package net.suzumiya.crosstie.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * GTNHLib の ModelISBRH 経由でブロックパーティクルアイコンを取得するユーティリティ。
 *
 * <p>GTNHLib 0.10.0 で {@code ModelISBRH.INSTANCE} が
 * {@code static ModelISBRH} から {@code static ThreadLocal<ModelISBRH>} に変更されました。
 * {@link #getModelIsbrhInstance()} は両方のケースに対応しています:
 * {@code INSTANCE} が {@link ThreadLocal} であれば {@code .get()} を呼び、
 * それ以外は直接インスタンスとして使用します。
 *
 * <p>また、{@code getParticleIcon} のシグネチャも変更されました:
 * <ul>
 *   <li>GTNHLib 0.10.0: {@code getParticleIcon(IBlockAccess, x, y, z)} (引数4つ)</li>
 *   <li>旧バージョン: {@code getParticleIcon(Block, IBlockAccess, x, y, z, side)} (引数6つ)</li>
 * </ul>
 * 新シグネチャを優先して試行し、見つからない場合は旧シグネチャにフォールバックします。
 */
public final class GtnhLibIconCompat {

    /** {@code ModelISBRH.INSTANCE} フィールドの値 (ThreadLocal または ModelISBRH インスタンス) */
    private static Object instanceFieldValue;
    /** INSTANCE が ThreadLocal かどうか */
    private static boolean instanceIsThreadLocal;
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
            if (instanceFieldValue == null || getParticleIconMethod == null || !(block instanceof Block)) {
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
        instanceFieldValue = instanceField.get(null);

        // GTNHLib 0.10.0: INSTANCE は ThreadLocal<ModelISBRH>
        // 旧バージョン: INSTANCE は ModelISBRH 直接
        instanceIsThreadLocal = instanceFieldValue instanceof ThreadLocal;

        // GTNHLib 0.10.0: getParticleIcon(IBlockAccess, x, y, z) — 4引数
        // 旧バージョン:   getParticleIcon(Block, IBlockAccess, x, y, z, side) — 6引数
        // 新シグネチャを優先して試行し、見つからない場合は旧シグネチャにフォールバック
        try {
            getParticleIconMethod = modelClass.getMethod(
                    "getParticleIcon",
                    IBlockAccess.class,
                    int.class,
                    int.class,
                    int.class);
            particleIconTakesBlock = false;
        } catch (NoSuchMethodException ignored) {
            getParticleIconMethod = modelClass.getMethod(
                    "getParticleIcon",
                    Block.class,
                    IBlockAccess.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class);
            particleIconTakesBlock = true;
        }
    }

    /**
     * ModelISBRH の実インスタンスを返します。
     *
     * <p>GTNHLib 0.10.0 では {@code INSTANCE} が {@link ThreadLocal} なので
     * {@code .get()} を呼んでスレッドローカルなインスタンスを取得します。
     * 旧バージョンでは {@code INSTANCE} が直接インスタンスなのでそのまま返します。
     */
    public static Object getModelIsbrhInstance() throws ReflectiveOperationException {
        lookupModelIsbrh();
        if (instanceFieldValue == null) {
            return null;
        }
        if (instanceIsThreadLocal) {
            return ((ThreadLocal<?>) instanceFieldValue).get();
        }
        return instanceFieldValue;
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
