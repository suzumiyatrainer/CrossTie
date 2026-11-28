package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.rail.RenderBlockLargeRail;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 2-5: {@link RenderBlockLargeRail#renderWorldBlock} が {@code useCache = false} で
 * 毎チャンク再構築時に 4 頂点 × 128 分割のベジェ探索を行っていたのを、
 * {@code TileEntityLargeRailBaseOptimizationMixin} のスレッドセーフ不変キャッシュ ({@code useCache = true}) 経由に切り替える。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = RenderBlockLargeRail.class, remap = false)
public abstract class RenderBlockLargeRailCacheMixin {

    @Redirect(
            method = "renderWorldBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/ngt/rtm/rail/TileEntityLargeRailBase;getBlockHeights(IIIFZ)[F",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private float[] crosstie$useCachedBlockHeights(TileEntityLargeRailBase tile, int x, int y, int z,
            float defaultHeight, boolean useCache) {
        return tile.getBlockHeights(x, y, z, defaultHeight, true);
    }
}
