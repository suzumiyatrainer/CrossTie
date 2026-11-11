package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.ctm.GlassPaneRenderer;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Angelica の {@link GlassPaneRenderer} において、板ガラス CTM ({@code MCPatcherForgeConfig.ConnectedTextures.glassPane})
 * がデフォルト {@code false} のままでも:
 * <ul>
 *   <li>標準 CTM ({@code ConnectedTextures.standard}) が有効であれば板ガラスの CTM も有効化する。</li>
 *   <li>また、{@code MixinRenderBlocks.tweakPaneIcons} / {@code tweakStainedPaneIcons} が先に
 *       {@code CTMUtils.getBlockIcon(origIcon, block, 0, meta)} を呼ぶことで {@code origIcon} がすでに {@code icons[0]}
 *       (単独ブロック用の4辺枠あり CTM タイル) に差し替えられており、上下接続のみの板ガラスや孤立した板ガラス等で
 *       {@code icons[face] == icons[0]} となった場合に {@code icons[face] != origIcon} が {@code false} と誤判定され
 *       CTM 描画および {@code setupPaneEdges} がスキップされる問題を防ぐため、
 *       {@code setupIcons} に渡す {@code origIcon} をブロック本来のアイコンに復元する。</li>
 * </ul>
 *
 * <p>なお、{@code setupIcons} 等は Angelica 2.2.30 では {@code static} メソッド、Angelica 2.2.39 ではインスタンスメソッドであるため、
 * ハンドラメソッドを {@code static} として定義することで両バージョンに対応する。
 */
@Mixin(value = GlassPaneRenderer.class, remap = false)
public abstract class AngelicaGlassPaneRendererFixMixin {

    @Redirect(
            method = "setupIcons",
            at = @At(
                    value = "FIELD",
                    target = "Ljss/notfine/config/MCPatcherForgeConfig$ConnectedTextures;glassPane:Z"),
            remap = false)
    private static boolean crosstie$enableGlassPaneCtmWhenStandardEnabled() {
        return jss.notfine.config.MCPatcherForgeConfig.ConnectedTextures.glassPane
                || jss.notfine.config.MCPatcherForgeConfig.ConnectedTextures.standard;
    }

    @ModifyVariable(
            method = "setupIcons",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private static IIcon crosstie$restoreTrueOrigIcon(
            IIcon origIcon,
            RenderBlocks renderBlocks,
            Block blockPane,
            IIcon origIconArg,
            int x, int y, int z) {
        if (renderBlocks != null && renderBlocks.blockAccess != null && blockPane != null) {
            int meta = renderBlocks.blockAccess.getBlockMetadata(x, y, z);
            IIcon raw = blockPane.getIcon(0, meta);
            if (raw != null) {
                return renderBlocks.getIconSafe(raw);
            }
        }
        return origIcon;
    }
}
