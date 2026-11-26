package net.suzumiya.crosstie.compat.bamboo;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

/**
 * 竹Mod ({@code BambooMod}) の縮小フィールド ({@code RenderMultiBlock}) 描画互換ヘルパー。
 *
 * <p>
 * Minecraft 1.7.10 の {@link RenderBlocks#renderFaceZNeg}（北面 / -Z）および
 * {@link RenderBlocks#renderFaceXPos}（東面 / +X）は、{@link RenderBlocks#field_152631_f} が
 * {@code false} のままだと部分ブロック ({@code renderMinX..renderMaxX} や
 * {@code renderMinZ..renderMaxZ} が {@code 0..1} の部分区間) の U 座標が
 * {@code (1.0 - max)..(1.0 - min)} ではなく {@code min..max} で計算されてしまい、
 * 縮小フィールド内の各サブブロックのテクスチャが不連続・反転して崩れる。
 *
 * <p>
 * バニラの階段 ({@code renderBlockStairs}) やフェンス ({@code renderBlockFence}) と同様に、
 * {@code renderStandardBlock} 呼び出しの間だけ {@code field_152631_f = true} を設定し、
 * 終了後に {@code finally} で元の値へ復元する。
 */
public final class BambooRenderCompat {

    private BambooRenderCompat() {
    }

    public static boolean renderStandardBlockWithUvFix(RenderBlocks renderBlocks, Block block, int x, int y, int z) {
        boolean prev = renderBlocks.field_152631_f;
        renderBlocks.field_152631_f = true;
        try {
            return renderBlocks.renderStandardBlock(block, x, y, z);
        } finally {
            renderBlocks.field_152631_f = prev;
        }
    }
}
