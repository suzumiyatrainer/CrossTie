package net.suzumiya.crosstie.mixins.recipeplus;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RecipePlusMod の {@code BlockExStairsGrass} で {@link Block#useNeighborBrightness} が
 * {@code false} のままになっており、階段の内角・段部分が暗く描画される問題を修正する Mixin。
 */
@Mixin(targets = "com.RecipePlus.block.BlockExStairsGrass", remap = false)
public abstract class BlockExStairsGrassMixin extends BlockStairs {

    protected BlockExStairsGrassMixin(Block modelBlock, int meta) {
        super(modelBlock, meta);
    }

    @Inject(method = "<init>()V", at = @At("RETURN"), remap = false)
    private void crosstie$enableNeighborBrightness(CallbackInfo ci) {
        this.useNeighborBrightness = true;
    }
}
