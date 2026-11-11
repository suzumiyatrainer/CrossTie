package net.suzumiya.crosstie.mixins.recipeplus;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * バニラのガラス・色付きガラス・氷 ({@link BlockBreakable}) の隣に
 * RecipePlusMod のガラス系ブロックが全面接触している場合、バニラガラス側の接合面もカリングする Mixin。
 */
@Mixin(BlockBreakable.class)
public abstract class BlockBreakableRecipePlusMixin extends Block {

    protected BlockBreakableRecipePlusMixin(Material material) {
        super(material);
    }

    @Inject(method = { "shouldSideBeRendered", "func_149646_a" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullAgainstRecipePlusGlass(
            IBlockAccess world, int x, int y, int z, int side,
            CallbackInfoReturnable<Boolean> cir) {
        if (RecipePlusGlassCompat.shouldCullVanillaBreakableSide(this, world, x, y, z, side)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}

