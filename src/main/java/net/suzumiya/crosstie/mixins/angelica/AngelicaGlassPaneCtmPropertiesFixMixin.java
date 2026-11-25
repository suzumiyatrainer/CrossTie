package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Properties;

/**
 * Angelica (MCPatcherForge CTM) におけるガラス系 CTM 定義の制御および板ガラス CTM 修正を行う Mixin。
 *
 * <ol>
 *   <li><b>CTM 無効化ゲート</b>:
 *       {@link CrossTieConfig#enableGlassConnectedTextures} が {@code false} の場合、
 *       ガラス系 CTM プロパティ（通常ガラス、板ガラス、各色色付きガラス、各色色付き板ガラス、遮光ガラス）の
 *       {@code method} を {@code "fixed"} に、{@code tiles} を {@code "0"} に上書きし、
 *       26.3 の単体テクスチャ (0.png) を維持したまま隣接結合だけを完全に無効化する。</li>
 *   <li><b>板ガラス CTM の整合性修正</b>:
 *       {@link CrossTieConfig#enableGlassConnectedTextures} が {@code true} の場合、
 *       {@code connect=sides} を {@code block} に修正し、
 *       板ガラスの {@code method=compact} を {@code compact_expanded} に昇格させて正常動作させる。</li>
 * </ol>
 */
@Mixin(value = PropertiesFile.class, remap = false)
public abstract class AngelicaGlassPaneCtmPropertiesFixMixin {

    @Shadow(remap = false)
    protected ResourceLocation resource;

    @Shadow(remap = false)
    protected Properties properties;

    @Unique
    private boolean crosstie$isGlassCtmProperty() {
        if (this.resource != null) {
            String path = this.resource.getResourcePath();
            if (path != null) {
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.contains("/ctm/glass")
                        || lower.contains("/ctm/stained_glass")
                        || lower.contains("/ctm/tinted_glass")
                        || lower.endsWith("/glass.properties")
                        || lower.endsWith("/glasspane.properties")
                        || lower.endsWith("/glass_pane.properties")
                        || lower.contains("stained_glass")
                        || lower.contains("tinted_glass")) {
                    return true;
                }
            }
        }
        if (this.properties != null) {
            String matchBlocks = this.properties.getProperty("matchBlocks");
            if (matchBlocks != null) {
                String mb = matchBlocks.toLowerCase(Locale.ROOT);
                if (mb.contains("minecraft:glass")
                        || mb.contains("minecraft:glass_pane")
                        || mb.contains("minecraft:stained_glass")
                        || mb.contains("minecraft:stained_glass_pane")
                        || mb.contains("kwsmmod:tinted_glass")
                        || mb.contains("glass")
                        || mb.contains("tinted")
                        || mb.contains("20")
                        || mb.contains("95")
                        || mb.contains("102")
                        || mb.contains("160")) {
                    return true;
                }
            }
            String matchTiles = this.properties.getProperty("matchTiles");
            if (matchTiles != null) {
                String mt = matchTiles.toLowerCase(Locale.ROOT);
                if (mt.contains("glass") || mt.contains("tinted")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Inject(method = "getString", at = @At("RETURN"), cancellable = true, remap = false)
    private void crosstie$fixGlassPaneCtmProperties(
            String key, String defaultValue,
            CallbackInfoReturnable<String> cir) {
        String val = cir.getReturnValue();
        if (key == null) {
            return;
        }

        // 1. ガラス系 CTM の無効化ゲート (enableGlassConnectedTextures == false)
        if (!CrossTieConfig.enableGlassConnectedTextures && crosstie$isGlassCtmProperty()) {
            if ("method".equals(key)) {
                cir.setReturnValue("fixed");
                return;
            } else if ("tiles".equals(key)) {
                cir.setReturnValue("0");
                return;
            }
        }

        if (val == null) {
            return;
        }

        // 2. enableGlassConnectedTextures == true の場合のみ既存の compact / compact_expanded 処理を適用
        if (CrossTieConfig.enableGlassConnectedTextures) {
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
                } else if (this.properties != null) {
                    String matchBlocks = this.properties.getProperty("matchBlocks");
                    if (matchBlocks != null
                            && (matchBlocks.contains("glass_pane") || matchBlocks.contains("stained_glass_pane")
                                    || matchBlocks.contains("102") || matchBlocks.contains("160"))) {
                        cir.setReturnValue("compact_expanded");
                    }
                }
            }
        }
    }
}
