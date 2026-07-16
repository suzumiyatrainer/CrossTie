package net.suzumiya.crosstie.mixins.optifine;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * OptiFine Shaders 環境で NGTTessellator の描画が無視される問題を修正する。
 *
 * <h3>問題の詳細</h3>
 * <p>OptiFine Shaders は描画パイプラインを FBO (Framebuffer Object) 経由に切り替え、
 * vanilla {@code Tessellator} にのみフックを掛ける。
 * RTM が使用する {@code NGTTessellator} は独自の {@code glDrawArrays} 直呼びで描画するため、
 * シェーダー有効時はレンダリングが完全に無視され、アンカー線などが描画されない。</p>
 *
 * <h3>修正内容</h3>
 * <p>{@code NGTTessellator.draw()} 内の {@code AngelicaCompat.isAvailable()} 呼び出しを
 * インターセプトし、OptiFine Shaders が有効な場合も {@code true} を返す。
 * これにより KaizPatchX が実装済みの {@code drawWithMinecraftTessellator()} パス
 * (vanilla {@code Tessellator} 経由) が選択され、OptiFine の描画フックが正常に機能する。</p>
 *
 * <p>このMixinは Angelica が存在しない場合のみ適用される (CrossTieMixinPlugin 参照)。
 * Angelica 環境での挙動は一切変化しない。</p>
 */
@SuppressWarnings("all")
@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorShaderCompatMixin {

    // OptiFine Shaders (shadersmod.client.Shaders) の shaderPackLoaded フィールドをキャッシュ
    private static java.lang.reflect.Field crosstie$shadersLoadedField = null;
    private static boolean crosstie$shadersFieldResolved = false;

    /**
     * NGTTessellator.draw() 内の AngelicaCompat.isAvailable() を横取りする。
     * OptiFine Shaders 有効時は true を返し、vanilla Tessellator 経由の描画を強制する。
     */
    @Redirect(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/kaiz/kaizpatch/compat/AngelicaCompat;isAvailable()Z",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private boolean crosstie$redirectIsAvailableForShaders() {
        // このMixinは Angelica が存在しない環境のみ適用されるため、
        // AngelicaCompat.isAvailable() は常に false のはず。
        // OptiFine Shaders が有効な場合のみ true を返す。
        return crosstie$isOptiFineShadersActive();
    }

    /**
     * OptiFine Shaders (shadersmod) が現在有効かどうかを判定する。
     * フィールド参照をキャッシュして毎フレームの反射コストを最小化する。
     */
    private static boolean crosstie$isOptiFineShadersActive() {
        if (!crosstie$shadersFieldResolved) {
            crosstie$shadersFieldResolved = true;
            try {
                Class<?> shadersClass = Class.forName("shadersmod.client.Shaders");
                // shaderPackLoaded フィールドを優先
                try {
                    crosstie$shadersLoadedField = shadersClass.getDeclaredField("shaderPackLoaded");
                    crosstie$shadersLoadedField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                    // フィールドが見つからない場合はメソッド経由で判定（下記フォールバック）
                }
            } catch (ClassNotFoundException ignored) {
                // shadersmod が存在しない = シェーダー機能なし OptiFine
            }
        }

        if (crosstie$shadersLoadedField != null) {
            try {
                return (boolean) crosstie$shadersLoadedField.get(null);
            } catch (Exception ignored) {
                return false;
            }
        }

        // shadersLoadedField が解決できなかった場合のフォールバック: メソッド呼び出し
        try {
            Class<?> shadersClass = Class.forName("shadersmod.client.Shaders");
            return (boolean) shadersClass.getMethod("isActive").invoke(null);
        } catch (Exception ignored) {
            return false;
        }
    }
}
