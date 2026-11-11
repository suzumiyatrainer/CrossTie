package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica (MCPatcherForge CTM) にバンドルされている板ガラス用 CTM 定義
 * ({@code glasspane.properties} および各色 {@code *_stained_glasspane.properties}) の不整合を修正する Mixin。
 *
 * <p>Angelica 2.2.x の内蔵 {@code *glasspane.properties} には以下の問題があり、
 * 板ガラスの Connected Textures (CTM) が完全に無効化されてしまう:
 * <ol>
 *   <li>{@code connect=sides} が指定されているが、{@code TileOverride} の switch 文は
 *       {@code block / tile / material / list} しか受け付けず、{@code invalid connect type sides}
 *       エラーで {@code properties.errorCount > 0} となり {@code TileOverride.create()} が {@code null} を返して破棄される。</li>
 *   <li>{@code method=compact} が指定されているが、{@code GlassPaneRenderer} は {@code RenderBlocks.renderFace*}
 *       を呼ばずに独自の頂点発行を行うため {@code CompactCtmQuadProcessor} が介入できず、
 *       {@code CTMCompact.getTileWorld_Impl()} が {@code origIcon} をそのまま返すことで
 *       {@code GlassPaneRenderer.setupIcons()} が {@code icons[face] != origIcon} を満たさず {@code false} になる。
 *       47枚の結合テクスチャを動的生成する {@code compact_expanded} に変換することで {@code GlassPaneRenderer} が正常動作する。</li>
 * </ol>
 */
@Mixin(value = PropertiesFile.class, remap = false)
public abstract class AngelicaGlassPaneCtmPropertiesFixMixin {

    @Shadow(remap = false)
    protected ResourceLocation resource;

    @Inject(method = "getString", at = @At("RETURN"), cancellable = true, remap = false)
    private void crosstie$fixGlassPaneCtmProperties(
            String key, String defaultValue,
            CallbackInfoReturnable<String> cir) {
        String val = cir.getReturnValue();
        if (val == null || key == null) {
            return;
        }
        if ("connect".equals(key) && "sides".equalsIgnoreCase(val.trim())) {
            cir.setReturnValue("block");
        } else if ("metadata".equals(key) && this.resource != null) {
            String path = this.resource.getResourcePath();
            if (path != null && (path.endsWith("/glasspane.properties") || path.endsWith("/glass_pane.properties"))) {
                cir.setReturnValue("");
            }
        } else if ("method".equals(key)
                && ("compact".equalsIgnoreCase(val.trim()) || "ctm_compact".equalsIgnoreCase(val.trim()))
                && this.resource != null) {
            String path = this.resource.getResourcePath();
            if (path != null && (path.endsWith("glasspane.properties") || path.endsWith("glass_pane.properties"))) {
                cir.setReturnValue("compact_expanded");
            } else {
                String matchBlocks = ((PropertiesFile) (Object) this).getString("matchBlocks", "");
                if (matchBlocks != null
                        && (matchBlocks.contains("glass_pane") || matchBlocks.contains("stained_glass_pane")
                                || matchBlocks.contains("102") || matchBlocks.contains("160"))) {
                    cir.setReturnValue("compact_expanded");
                }
            }
        }
    }
}

