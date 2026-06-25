package net.suzumiya.crosstie.mixins.optifine;

import net.minecraft.client.renderer.OpenGlHelper;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine/FastCraft 環境で {@code OpenGlHelper.lightmapTexUnit} が {@code 0}（= GL_TEXTURE0）に
 * なることで、{@code GLHelper.setBrightness()} がメインテクスチャの UV 座標を上書きする問題を修正する。
 *
 * <h3>問題の詳細</h3>
 * <p>RTM の {@code GLHelper.setBrightness()} は次のように実装されている:</p>
 * <pre>
 * OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) x, (float) y);
 * </pre>
 * <p>バニラ Minecraft では {@code lightmapTexUnit = GL13.GL_TEXTURE1}（= 33985）であり、
 * ライトマップ専用のテクスチャユニットに座標を設定する。</p>
 *
 * <p>しかし OptiFine または FastCraft 環境では {@code lightmapTexUnit} が {@code 0}
 * （= GL_TEXTURE0 = メインテクスチャユニット）になることがある。
 * この状態で呼び出された {@code glMultiTexCoord2f(0, 240, 240)} は
 * メインテクスチャの UV 座標を {@code (240, 240)} に上書きするため、
 * DisplayList コンパイル中に記録されると再生時に全頂点 UV が破壊され、
 * {@code LargeRail} が緑の縦線として表示される。</p>
 *
 * <h3>修正内容</h3>
 * <p>{@code lightmapTexUnit == 0} の場合、{@code setBrightness()} の呼び出しをスキップする。
 * OptiFine/FastCraft はライトマップを独自の仕組みで管理するため、
 * このスキップによってライティングに実質的な影響は出ない。</p>
 *
 * <h3>Angelica との分離</h3>
 * <p>この Mixin は {@code CrossTieMixinPlugin.shouldApplyMixin()} において
 * {@code AngelicaGlsm} が存在する場合に絶対に適用されない。
 * Angelica 環境では {@code lightmapTexUnit} は正常値（33985）を保つため、
 * 仮に適用されたとしても修正ロジックが発動することはない（二重の安全網）。</p>
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.GLHelper", remap = false)
public class RailBrightnessDisplayListSafeMixin {

    /**
     * {@code OpenGlHelper.lightmapTexUnit} が {@code 0}（= GL_TEXTURE0）の場合、
     * ライトマップ座標の設定をスキップして UV 座標破壊を防ぐ。
     */
    @Inject(method = "setBrightness", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$skipBrightnessWhenLightmapTexUnitZero(int par1, CallbackInfo ci) {
        if (!CrossTieConfig.fixOptiFineRailBrightness) {
            return;
        }
        // lightmapTexUnit == 0 は OptiFine/FastCraft 環境でのみ発生する。
        // この状態で setLightmapTextureCoords を呼ぶと GL_TEXTURE0（メインテクスチャ）の
        // UV 座標が破壊されるためスキップする。
        if (OpenGlHelper.lightmapTexUnit == 0) {
            ci.cancel();
        }
    }
}
