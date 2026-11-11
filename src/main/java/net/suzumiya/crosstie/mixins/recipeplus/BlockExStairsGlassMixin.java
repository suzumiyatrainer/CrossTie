package net.suzumiya.crosstie.mixins.recipeplus;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.world.IBlockAccess;
import net.suzumiya.crosstie.compat.recipeplus.IRecipePlusGlassBlock;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RecipePlusMod の {@code BlockExStairs} に対し、以下を修正する Mixin:
 * <ul>
 *   <li>色付きガラス階段・氷階段がレンダーパス 0 (不透明/カットアウト) で描画されていた問題を修正し、
 *       元ブロックの {@link Block#getRenderBlockPass()} を委譲する。</li>
 *   <li>透過ブロックの階段の光透過度 (lightOpacity) を 0 に設定する。</li>
 *   <li>ガラス・氷系階段の隣接面カリング ({@link Block#shouldSideBeRendered}) を実装する。</li>
 * </ul>
 */
@Mixin(targets = "com.RecipePlus.block.BlockExStairs", remap = false)
public abstract class BlockExStairsGlassMixin extends BlockStairs implements IRecipePlusGlassBlock {

    @Shadow(remap = false)
    protected Block baseBlock;

    @Unique
    private int crosstie$baseBlockMeta;

    protected BlockExStairsGlassMixin(Block modelBlock, int meta) {
        super(modelBlock, meta);
    }

    @Inject(method = "<init>(Lnet/minecraft/block/Block;I)V", at = @At("RETURN"), remap = false)
    private void crosstie$onInit2(Block block, int meta, CallbackInfo ci) {
        this.crosstie$baseBlockMeta = meta;
        if (block != null && !block.isOpaqueCube()) {
            this.setLightOpacity(0);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/block/Block;IF)V", at = @At("RETURN"), remap = false)
    private void crosstie$onInit3(Block block, int meta, float slipperiness, CallbackInfo ci) {
        this.crosstie$baseBlockMeta = meta;
        if (block != null && !block.isOpaqueCube()) {
            this.setLightOpacity(0);
        }
    }

    @Override
    public Block crosstie$getBaseBlock() {
        return this.baseBlock;
    }

    @Override
    public int crosstie$getBaseBlockMeta() {
        return this.crosstie$baseBlockMeta;
    }

    @Override
    public int crosstie$getShapeKind() {
        return IRecipePlusGlassBlock.SHAPE_STAIRS;
    }

    @Override
    public int getRenderBlockPass() {
        if (this.baseBlock != null) {
            return this.baseBlock.getRenderBlockPass();
        }
        return super.getRenderBlockPass();
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

