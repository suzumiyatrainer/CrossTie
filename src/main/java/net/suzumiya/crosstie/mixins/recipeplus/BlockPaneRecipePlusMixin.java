package net.suzumiya.crosstie.mixins.recipeplus;

import net.minecraft.block.BlockPane;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 板ガラス・鉄格子 ({@link BlockPane}) が RecipePlusMod のハーフブロック ({@code BlockExSlab})・
 * 1/4ブロック ({@code BlockExQuarterSlab})・階段 ({@code BlockExStairs}) の接触面に接続できるようにする Mixin。
 */
@Mixin(BlockPane.class)
public abstract class BlockPaneRecipePlusMixin {

    @Inject(method = "canPaneConnectTo", at = @At("RETURN"), cancellable = true, remap = false)
    private void crosstie$connectToRecipePlusBlocks(
            IBlockAccess world, int x, int y, int z, ForgeDirection dir,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && RecipePlusGlassCompat.canPaneConnectToRecipePlus(world, x, y, z, dir)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}

