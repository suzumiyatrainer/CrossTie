package net.suzumiya.crosstie.compat.recipeplus;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;

/**
 * RecipePlusMod (1.3.0 / 1.6.0 共通) の Ex草ブロック判定ヘルパー。
 *
 * <p>マルチスレッドチャンクビルド (Angelica / Celeritas) からも安全に呼び出せるよう
 * {@link ConcurrentHashMap} でクラス単位にキャッシュする。
 */
public final class RecipePlusGrassCompat {

    private static final ConcurrentHashMap<Class<?>, Boolean> GRASS_BLOCK_CLASS_CACHE = new ConcurrentHashMap<>();

    private RecipePlusGrassCompat() {
    }

    public static boolean isRecipePlusGrassBlock(Block block) {
        if (block == null) {
            return false;
        }
        Class<?> clazz = block.getClass();
        Boolean cached = GRASS_BLOCK_CLASS_CACHE.get(clazz);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean match = isRecipePlusGrassClassName(clazz.getName());
        GRASS_BLOCK_CLASS_CACHE.putIfAbsent(clazz, Boolean.valueOf(match));
        return match;
    }

    private static boolean isRecipePlusGrassClassName(String name) {
        return "com.RecipePlus.block.BlockExStairsGrass".equals(name)
                || "com.RecipePlus.block.BlockExSlabGrass".equals(name)
                || "com.RecipePlus.block.BlockExQuarterSlabGrass".equals(name)
                || "com.RecipePlus.block.BlockExFenceGrass".equals(name)
                || "com.RecipePlus.block.BlockExFenceGateGrass".equals(name)
                || "com.RecipePlus.block.BlockExWallGrass".equals(name);
    }
}
