package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.ctm.CTMTextureGenerator;
import com.prupe.mcpatcher.ctm.TileOverrideImpl.CTMCompactExpanded;
import com.prupe.mcpatcher.mal.tile.TileLoader;
import java.awt.image.BufferedImage;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica の {@link CTMTextureGenerator#generateTextures} における以下の2つのバグを修正する Mixin:
 *
 * <ol>
 *   <li><b>{@code cVert} と {@code cHori} の取り違え</b>:
 *       MCPatcher/OptiFine の compact CTM 形式（および Angelica 自身の {@code CompactCtmQuadProcessor}）において、
 *       {@code 2.png} ({@code compactIcons[2]}) は水平接続用（上下に枠線）、
 *       {@code 3.png} ({@code compactIcons[3]}) は垂直接続用（左右に枠線）であるにもかかわらず、
 *       {@code CTMTextureGenerator} では {@code cVert = compactIcons[2]; cHori = compactIcons[3];} と逆に代入されているため、
 *       {@code compact_expanded} で生成された47枚の結合テクスチャの枠線が縦横あべこべになり、
 *       横に並べた板ガラスの継ぎ目に縦枠が残り上下の枠が消えてしまう。</li>
 *   <li><b>同一ディレクトリ内の {@code 0.png..4.png} 上書き汚染</b>:
 *       {@code CTMTextureGenerator} が生成した47枚のテクスチャを {@code 0.png..46.png} という名前で
 *       {@code TileLoader} に登録するため、同じディレクトリにある通常のガラスブロック用 {@code glass.properties}
 *       ({@code method=compact}) が参照する {@code 0.png..4.png} の画像キャッシュ ({@code tileImages}) を
 *       展開後の47枚用タイルで上書き破壊してしまう。
 *       生成タイル名を {@code ctm_expanded_0..46} に分離することで、{@code 0.png..4.png} の汚染を防ぐ
 *       （{@code GeneratedCTMAtlasSprite.getMetaLoc} は同じディレクトリの {@code 0.png} を参照するためアニメーションメタデータも維持される）。</li>
 * </ol>
 */
@Mixin(value = CTMTextureGenerator.class, remap = false)
public abstract class AngelicaCtmTextureGeneratorFixMixin {

    @Inject(method = "generateTextures", at = @At("HEAD"), remap = false)
    private static void crosstie$swapCompactVertAndHori(
            BufferedImage[] compactIcons,
            CTMCompactExpanded override,
            ResourceLocation propsLoc,
            ResourceLocation blankResource,
            CallbackInfo ci) {
        if (compactIcons != null && compactIcons.length >= 4) {
            BufferedImage tmp = compactIcons[2];
            compactIcons[2] = compactIcons[3];
            compactIcons[3] = tmp;
        }
    }

    @Redirect(
            method = "generateTextures",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/prupe/mcpatcher/mal/tile/TileLoader;parseTileAddress(Lnet/minecraft/util/ResourceLocation;Ljava/lang/String;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/util/ResourceLocation;"),
            remap = false)
    private static ResourceLocation crosstie$useDistinctExpandedTileAddress(
            ResourceLocation propertiesAddress,
            String value,
            ResourceLocation blankResource) {
        return TileLoader.parseTileAddress(propertiesAddress, "ctm_expanded_" + value, blankResource);
    }
}

