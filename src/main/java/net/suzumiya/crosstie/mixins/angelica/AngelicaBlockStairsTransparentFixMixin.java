package net.suzumiya.crosstie.mixins.angelica;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica (NotFine) の {@code MixinBlockStairs#isFaceNonObstructing} が
 * {@code getRenderBlockPass() == 1} のみ非遮蔽扱いにしていたため、
 * 透明ガラス階段 ({@code Blocks.glass} はレンダーパス 0 かつ {@code !isOpaqueCube()}) の
 * 真下・背後のブロック面がカリングされて X-Ray になる問題を修正する Mixin。
 */
@Mixin(value = BlockStairs.class, priority = 1100)
public abstract class AngelicaBlockStairsTransparentFixMixin {

    @Shadow
    private Block field_150149_b;

    @Inject(method = "isFaceNonObstructing", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void crosstie$allowTransparentStairsNonObstructing(
            IBlockAccess worldIn,
            int x,
            int y,
            int z,
            int side,
            double otherMinX,
            double otherMinY,
            double otherMinZ,
            double otherMaxX,
            double otherMaxY,
            double otherMaxZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.field_150149_b != null && !this.field_150149_b.isOpaqueCube()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
