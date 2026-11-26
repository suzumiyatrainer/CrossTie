package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.suzumiya.crosstie.compat.recipeplus.IRecipePlusGlassBlock;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica (MCPatcherForge CTM) の {@link BlockStateMatcher#match(IBlockAccess, int, int, int)} で、
 * RecipePlusMod のガラス系拡張ブロック (階段・ハーフ・クォーター) を
 * 元ブロック ({@code Blocks.glass} / {@code Blocks.stained_glass} 等) および
 * 元ブロックの色メタデータとして照合させる Mixin。
 */
@Mixin(value = BlockStateMatcher.class, remap = false)
public abstract class AngelicaBlockStateMatcherRecipePlusGlassMixin {

    @Shadow(remap = false)
    public abstract boolean match(Block block, int metadata);

    @Inject(method = "match(Lnet/minecraft/world/IBlockAccess;III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$matchRecipePlusGlass(
            IBlockAccess blockAccess,
            int x,
            int y,
            int z,
            CallbackInfoReturnable<Boolean> cir) {
        if (blockAccess == null) {
            return;
        }
        Block block = blockAccess.getBlock(x, y, z);
        if (block instanceof IRecipePlusGlassBlock) {
            IRecipePlusGlassBlock glass = (IRecipePlusGlassBlock) block;
            Block base = glass.crosstie$getBaseBlock();
            if (RecipePlusGlassCompat.isCullableGlassBase(base)) {
                cir.setReturnValue(Boolean.valueOf(this.match(base, glass.crosstie$getBaseBlockMeta())));
            }
        }
    }
}
