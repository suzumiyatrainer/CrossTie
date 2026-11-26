package net.suzumiya.crosstie.mixins.recipeplus;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGrassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * RecipePlusMod の Ex草ブロック (階段・ハーフ・クォーター・フェンス・フェンスゲート・壁) の
 * 描画がバニラの草ブロックと異なり側面・下面の土部分までバイオーム色で乗算されてしまう問題を修正する。
 *
 * <p>
 * CrossTie は refmap 非生成構成のため、SRG 名 ({@code func_147736_d},
 * {@code func_78386_a}, {@code func_147800_a}) と MCP 名の両方を明示して
 * {@code remap = false} で注入する。
 */
@Mixin(value = RenderBlocks.class, remap = false)
public abstract class RenderBlocksRecipePlusGrassMixin {

        @Unique
        private static void crosstie$applyUnmultipliedColor(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, float r, float g, float b) {
                if ((r != 1.0F || g != 1.0F || b != 1.0F) && RecipePlusGrassCompat.isRecipePlusGrassBlock(block)) {
                        float nr = r > 0.0F ? cr / r : cr;
                        float ng = g > 0.0F ? cg / g : cg;
                        float nb = b > 0.0F ? cb / b : cb;
                        tessellator.setColorOpaque_F(nr, ng, nb);
                } else {
                        tessellator.setColorOpaque_F(cr, cg, cb);
                }
        }

        // --- SRG (実機環境: func_147736_d / func_78386_a) ---

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 0), remap = false, require = 0)
        private void crosstie$fixGrassColorYNegSrg(Tessellator tessellator, float cr, float cg, float cb, Block block,
                        int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 2), remap = false, require = 0)
        private void crosstie$fixGrassColorZNegBaseSrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 3), remap = false, require = 0)
        private void crosstie$fixGrassColorZNegOverlaySrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 4), remap = false, require = 0)
        private void crosstie$fixGrassColorZPosBaseSrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 5), remap = false, require = 0)
        private void crosstie$fixGrassColorZPosOverlaySrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 6), remap = false, require = 0)
        private void crosstie$fixGrassColorXNegBaseSrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 7), remap = false, require = 0)
        private void crosstie$fixGrassColorXNegOverlaySrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 8), remap = false, require = 0)
        private void crosstie$fixGrassColorXPosBaseSrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78386_a(FFF)V", ordinal = 9), remap = false, require = 0)
        private void crosstie$fixGrassColorXPosOverlaySrg(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        // --- MCP (開発環境: setColorOpaque_F) ---

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 0), remap = false, require = 0)
        private void crosstie$fixGrassColorYNegMcp(Tessellator tessellator, float cr, float cg, float cb, Block block,
                        int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 2), remap = false, require = 0)
        private void crosstie$fixGrassColorZNegBaseMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 3), remap = false, require = 0)
        private void crosstie$fixGrassColorZNegOverlayMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 4), remap = false, require = 0)
        private void crosstie$fixGrassColorZPosBaseMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 5), remap = false, require = 0)
        private void crosstie$fixGrassColorZPosOverlayMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 6), remap = false, require = 0)
        private void crosstie$fixGrassColorXNegBaseMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 7), remap = false, require = 0)
        private void crosstie$fixGrassColorXNegOverlayMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 8), remap = false, require = 0)
        private void crosstie$fixGrassColorXPosBaseMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @Redirect(method = { "func_147736_d",
                        "renderStandardBlockWithColorMultiplier" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 9), remap = false, require = 0)
        private void crosstie$fixGrassColorXPosOverlayMcp(Tessellator tessellator, float cr, float cg, float cb,
                        Block block, int x, int y, int z, float r, float g, float b) {
                crosstie$applyUnmultipliedColor(tessellator, cr, cg, cb, block, r, g, b);
        }

        @ModifyVariable(method = { "func_147800_a",
                        "renderBlockAsItem" }, at = @At("STORE"), ordinal = 0, remap = false, require = 0)
        private boolean crosstie$fixGrassItemFlag(boolean isGrass, Block block, int meta, float brightness) {
                return isGrass || RecipePlusGrassCompat.isRecipePlusGrassBlock(block);
        }

        @Unique
        private void crosstie$renderGrassItemTopFace(RenderBlocks self, Block faceBlock, double x, double y, double z,
                        IIcon icon, Block itemBlock, int meta, float brightness) {
                if (RecipePlusGrassCompat.isRecipePlusGrassBlock(itemBlock) && itemBlock.getRenderType() != 0) {
                        int color = itemBlock.getRenderColor(meta);
                        float r = (float) (color >> 16 & 255) / 255.0F;
                        float g = (float) (color >> 8 & 255) / 255.0F;
                        float b = (float) (color & 255) / 255.0F;
                        Tessellator.instance.setColorOpaque_F(r * brightness, g * brightness, b * brightness);
                        self.renderFaceYPos(faceBlock, x, y, z, icon);
                } else {
                        self.renderFaceYPos(faceBlock, x, y, z, icon);
                }
        }

        @Redirect(method = { "func_147800_a",
                        "renderBlockAsItem" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;func_147806_b(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"), remap = false, require = 0)
        private void crosstie$tintGrassItemTopSrg(RenderBlocks self, Block faceBlock, double x, double y, double z,
                        IIcon icon, Block itemBlock, int meta, float brightness) {
                crosstie$renderGrassItemTopFace(self, faceBlock, x, y, z, icon, itemBlock, meta, brightness);
        }

        @Redirect(method = { "func_147800_a",
                        "renderBlockAsItem" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYPos(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"), remap = false, require = 0)
        private void crosstie$tintGrassItemTopMcp(RenderBlocks self, Block faceBlock, double x, double y, double z,
                        IIcon icon, Block itemBlock, int meta, float brightness) {
                crosstie$renderGrassItemTopFace(self, faceBlock, x, y, z, icon, itemBlock, meta, brightness);
        }
}
