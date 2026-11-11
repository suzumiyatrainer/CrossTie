package net.suzumiya.crosstie.compat.recipeplus;

import net.minecraft.block.Block;

/**
 * RecipePlusMod のガラス・氷系拡張ブロック (BlockExSlab, BlockExQuarterSlab, BlockExStairs) から
 * 元ブロック情報と形状種別を取得するための内部インターフェース。
 */
public interface IRecipePlusGlassBlock {

    int SHAPE_SLAB = 1;
    int SHAPE_QUARTER_SLAB = 2;
    int SHAPE_STAIRS = 3;

    /**
     * 元となるブロック (Blocks.glass, Blocks.stained_glass, Blocks.ice 等) を返す。
     */
    Block crosstie$getBaseBlock();

    /**
     * 元となるブロックのメタデータ (色ガラスのカラー値 0..15 等) を返す。
     */
    int crosstie$getBaseBlockMeta();

    /**
     * ブロックの形状種別 ({@link #SHAPE_SLAB}, {@link #SHAPE_QUARTER_SLAB}, {@link #SHAPE_STAIRS}) を返す。
     */
    int crosstie$getShapeKind();
}

