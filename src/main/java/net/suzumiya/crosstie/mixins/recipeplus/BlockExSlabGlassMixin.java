package net.suzumiya.crosstie.mixins.recipeplus;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.suzumiya.crosstie.compat.recipeplus.IRecipePlusGlassBlock;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * RecipePlusMod の {@code BlockExSlab} および {@code BlockExQuarterSlab} に
 * ガラス・色付きガラス・氷系の隣接面カリング ({@link Block#shouldSideBeRendered}) を実装する Mixin。
 */
@Mixin(targets = {
        "com.RecipePlus.block.BlockExSlab",
        "com.RecipePlus.block.BlockExQuarterSlab"
}, remap = false)
public abstract class BlockExSlabGlassMixin extends Block implements IRecipePlusGlassBlock {

    @Shadow(remap = false)
    protected Block baseBlock;

    @Shadow(remap = false)
    protected int baseBlockMetaData;

    @Unique
    private int crosstie$shapeKind;

    protected BlockExSlabGlassMixin(Material material) {
        super(material);
    }

    @Override
    public Block crosstie$getBaseBlock() {
        return this.baseBlock;
    }

    @Override
    public int crosstie$getBaseBlockMeta() {
        return this.baseBlockMetaData;
    }

    @Override
    public int crosstie$getShapeKind() {
        int kind = this.crosstie$shapeKind;
        if (kind == 0) {
            kind = this.getClass().getName().endsWith("QuarterSlab")
                    ? IRecipePlusGlassBlock.SHAPE_QUARTER_SLAB
                    : IRecipePlusGlassBlock.SHAPE_SLAB;
            this.crosstie$shapeKind = kind;
        }
        return kind;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        Boolean result = RecipePlusGlassCompat.shouldSideBeRendered(this, this, world, x, y, z, side);
        if (result != null) {
            return result.booleanValue();
        }
        return super.shouldSideBeRendered(world, x, y, z, side);
    }
}

