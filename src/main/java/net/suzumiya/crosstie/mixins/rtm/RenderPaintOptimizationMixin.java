package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.util.ColorUtil;
import jp.ngt.ngtlib.util.NGTUtilClient;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.RTMItem;
import jp.ngt.rtm.block.tileentity.RenderPaint;
import jp.ngt.rtm.block.tileentity.TileEntityPaint;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.suzumiya.crosstie.accessors.rtm.IPaintDisplayListHolder;
import net.suzumiya.crosstie.client.CrossTieBlendStateHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * 3-4: {@link RenderPaint} の DisplayList キャッシュ化 ＋ 2D Greedy Meshing（矩形結合最適化）。
 *
 * <ul>
 *   <li>GL ステート変更（{@code GL_TEXTURE_2D}, {@code GL_BLEND}, {@code GL_LIGHTING}, {@code GL_ALPHA_TEST},
 *       {@code depthMask}）と Crowbar 保持時描画はすべて <b>DisplayList の外側</b> に配置し、
 *       Angelica ({@code DisplayListVBOBuilder}) と OptiFine ({@code Shaders.disableTexture2D}) の双方で 100% 安全。</li>
 *   <li>DisplayList 内には純粋な {@code Tessellator.instance} の結合クワッドのみを格納し、
 *       最大 1,536 ピクセル (6,144 頂点) を数十頂点の 1 draw call へ圧縮する。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = RenderPaint.class, remap = false)
public abstract class RenderPaintOptimizationMixin {

    @Unique
    private static final double CROSSTIE_PIXEL_SIZE = 1.0D / 16.0D;
    @Unique
    private static final double CROSSTIE_GAP = 1.0D / 64.0D;
    @Unique
    private static final double CROSSTIE_INV_GAP = 1.0D - CROSSTIE_GAP;

    /**
     * @author CrossTie
     * @reason 静的ペイントを 2D Greedy Meshing で DisplayList キャッシュ化し、毎フレーム最大 6,144 頂点の CPU 再構築を排除。
     */
    @Overwrite
    private void renderPaintAt(TileEntityPaint tileEntity, double par2, double par4, double par6, float par8) {
        IPaintDisplayListHolder holder = (IPaintDisplayListHolder) tileEntity;
        DisplayList dl = holder.crosstie$getPaintGLList();
        boolean dirty = holder.crosstie$isPaintDirty();

        if (dirty || (!holder.crosstie$isPaintEmpty() && !GLHelper.isValid(dl))) {
            boolean anyFace = false;
            for (int f = 0; f < 6; f++) {
                if (tileEntity.hasColor(f)) {
                    anyFace = true;
                    break;
                }
            }
            if (!anyFace) {
                if (dl != null) {
                    GLHelper.deleteGLList(dl);
                    holder.crosstie$setPaintGLList(null);
                    dl = null;
                }
                holder.crosstie$setPaintEmpty(true);
                holder.crosstie$setPaintDirty(false);
            } else {
                if (!GLHelper.isValid(dl)) {
                    dl = GLHelper.generateGLList(dl);
                    holder.crosstie$setPaintGLList(dl);
                }
                boolean emitted = false;
                GLHelper.startCompile(dl);
                try {
                    emitted = crosstie$tessellatePaintGreedy(tileEntity);
                } finally {
                    GLHelper.endCompile();
                }
                holder.crosstie$setPaintEmpty(!emitted);
                holder.crosstie$setPaintDirty(false);
            }
        }

        ItemStack stack = NGTUtilClient.getMinecraft().thePlayer.inventory.getCurrentItem();
        boolean holdingCrowbar = (stack != null && stack.getItem() == RTMItem.crowbar);
        boolean hasGeometry = !holder.crosstie$isPaintEmpty() && GLHelper.isValid(dl);

        if (!hasGeometry && !holdingCrowbar) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glTranslatef((float) par2, (float) par4, (float) par6);

        if (hasGeometry) {
            // GL ステート変更はすべて DisplayList 外側（Angelica / OptiFine Shaders 双方で必須）
            GLHelper.disableLighting();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            boolean blendChanged = CrossTieBlendStateHelper.enterPass1Blend();
            GL11.glDepthMask(false);

            GLHelper.callList(dl);

            GL11.glDepthMask(true);
            CrossTieBlendStateHelper.exitPass1Blend(blendChanged);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GLHelper.enableLighting();
        }

        if (holdingCrowbar) {
            GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            RTMCore.proxy.renderMissingModel();
        }

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    /**
     * 6 面それぞれについて 2D Greedy Meshing で同一 (color, alpha) の矩形を極大結合して出力する。
     */
    @Unique
    private static boolean crosstie$tessellatePaintGreedy(TileEntityPaint tileEntity) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        boolean emitted = false;
        int[] visitedMask = new int[16];

        for (int face = 0; face < 6; ++face) {
            if (!tileEntity.hasColor(face)) {
                continue;
            }
            for (int m = 0; m < 16; m++) {
                visitedMask[m] = 0;
            }

            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ++k) {
                    if ((visitedMask[j] & (1 << k)) != 0) {
                        continue;
                    }
                    int alpha = tileEntity.getAlpha(j, k, face);
                    if (alpha == 0) {
                        visitedMask[j] |= (1 << k);
                        continue;
                    }
                    int color = tileEntity.getColor(j, k, face);

                    // k 方向へ最大幅 w を伸ばす
                    int w = 1;
                    while (k + w < 16
                            && (visitedMask[j] & (1 << (k + w))) == 0
                            && tileEntity.getAlpha(j, k + w, face) == alpha
                            && tileEntity.getColor(j, k + w, face) == color) {
                        w++;
                    }

                    // j 方向へ同じ幅 w のまま最大高さ h を伸ばす
                    int h = 1;
                    outer:
                    while (j + h < 16) {
                        int rowMask = visitedMask[j + h];
                        for (int dk = 0; dk < w; dk++) {
                            int ck = k + dk;
                            if ((rowMask & (1 << ck)) != 0
                                    || tileEntity.getAlpha(j + h, ck, face) != alpha
                                    || tileEntity.getColor(j + h, ck, face) != color) {
                                break outer;
                            }
                        }
                        h++;
                    }

                    // 訪問マーク
                    int spanBits = ((1 << w) - 1) << k;
                    for (int dj = 0; dj < h; dj++) {
                        visitedMask[j + dj] |= spanBits;
                    }

                    double u0 = (double) j * CROSSTIE_PIXEL_SIZE;
                    double u1 = (double) (j + h) * CROSSTIE_PIXEL_SIZE;
                    double v0 = (double) k * CROSSTIE_PIXEL_SIZE;
                    double v1 = (double) (k + w) * CROSSTIE_PIXEL_SIZE;
                    double d2 = CROSSTIE_GAP;
                    double d3 = CROSSTIE_INV_GAP;

                    switch (face) {
                        case 0:
                            tessellator.setColorRGBA_I(ColorUtil.multiplicating(0x808080, color), alpha);
                            tessellator.addVertex(u0, d3, v1);
                            tessellator.addVertex(u0, d3, v0);
                            tessellator.addVertex(u1, d3, v0);
                            tessellator.addVertex(u1, d3, v1);
                            break;
                        case 1:
                            tessellator.setColorRGBA_I(color, alpha);
                            tessellator.addVertex(u1, d2, v1);
                            tessellator.addVertex(u1, d2, v0);
                            tessellator.addVertex(u0, d2, v0);
                            tessellator.addVertex(u0, d2, v1);
                            break;
                        case 2:
                            tessellator.setColorRGBA_I(ColorUtil.multiplicating(0xCCCCCC, color), alpha);
                            tessellator.addVertex(u1, v1, d3);
                            tessellator.addVertex(u1, v0, d3);
                            tessellator.addVertex(u0, v0, d3);
                            tessellator.addVertex(u0, v1, d3);
                            break;
                        case 3:
                            tessellator.setColorRGBA_I(ColorUtil.multiplicating(0xCCCCCC, color), alpha);
                            tessellator.addVertex(u0, v1, d2);
                            tessellator.addVertex(u0, v0, d2);
                            tessellator.addVertex(u1, v0, d2);
                            tessellator.addVertex(u1, v1, d2);
                            break;
                        case 4:
                            tessellator.setColorRGBA_I(ColorUtil.multiplicating(0x999999, color), alpha);
                            tessellator.addVertex(d3, u1, v1);
                            tessellator.addVertex(d3, u1, v0);
                            tessellator.addVertex(d3, u0, v0);
                            tessellator.addVertex(d3, u0, v1);
                            break;
                        case 5:
                            tessellator.setColorRGBA_I(ColorUtil.multiplicating(0x999999, color), alpha);
                            tessellator.addVertex(d2, u0, v1);
                            tessellator.addVertex(d2, u0, v0);
                            tessellator.addVertex(d2, u1, v0);
                            tessellator.addVertex(d2, u1, v1);
                            break;
                    }
                    emitted = true;
                }
            }
        }
        tessellator.draw();
        return emitted;
    }
}
