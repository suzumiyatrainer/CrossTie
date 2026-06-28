package net.suzumiya.crosstie.mixins.splash;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import cpw.mods.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica の enableFontRenderer=false 時に発生するスプラッシュ画面黒化バグを修正する。
 *
 * <h3>根本原因</h3>
 * AngelicaのGLSMRedirectorはSplashProgress$3を含む全クラスのGL11呼び出しをバイトコードレベルで
 * {@link GLStateManager} 呼び出しにリダイレクトする。
 *
 * <p>{@code GLStateManager} クラスの静的フィールド初期化（クラスロード時）で
 * {@code RENDER_BACKEND.getInteger(GL_MAX_TEXTURE_IMAGE_UNITS)} を実行する。
 * このGL呼び出しには、現在のスレッドでGLコンテキストがアクティブである必要がある。</p>
 *
 * <p>{@code enableFontRenderer=true} の場合:<br>
 * {@code SplashFontRenderer.<init>()} が {@code BatchingFontRenderer} を生成し、
 * その中で {@code GLStateManager.glGetUniformLocation()} が呼ばれる。
 * これにより {@code GLStateManager} がクライアントスレッド（GLコンテキスト有効）でロードされる。</p>
 *
 * <p>{@code enableFontRenderer=false} の場合:<br>
 * {@code BatchingFontRenderer} が生成されないため、{@code GLStateManager} が
 * スプラッシュ描画スレッドの最初のGL呼び出し時に初めてロードされる可能性がある。
 * そのタイミングでGLコンテキストが未確立だと {@code MAX_TEXTURE_UNITS=0} となり、
 * テクスチャバインド時に {@code ArrayIndexOutOfBoundsException} が発生してスレッドがクラッシュ、
 * 画面が黒いまま固まる。</p>
 *
 * <h3>修正</h3>
 * {@code SplashProgress.start()} のHEAD（コンテキスト切り替え前・描画スレッド起動前）で
 * {@code GLStateManager.isSplashComplete()} を呼び出し、クライアントスレッド上で
 * GLStateManagerを強制的にロードする。これにより静的初期化時のGL呼び出しが
 * 有効なGLコンテキスト上で実行されることが保証される。
 */
@SuppressWarnings("deprecation")
@Mixin(value = SplashProgress.class, remap = false)
public class MixinSplashProgressEarlyGLStateManagerLoad {

    /**
     * SplashProgress.start() の最初（GLコンテキスト切り替え前、スプラッシュ描画スレッド起動前）に
     * GLStateManager クラスをクライアントスレッド上でロードする。
     *
     * <p>このインジェクションは Angelica の MixinSplashProgressCaching.angelica$initSplashTessellator()
     * と同じ HEAD に挿入されるが、それより先に実行されることを意図している（優先度で制御）。</p>
     *
     * <p>副作用なし：{@code GLStateManager.isSplashComplete()} は
     * {@code volatile boolean} フィールドを読むだけで、GL 呼び出しを一切行わない。</p>
     */
    @Inject(method = "start", at = @At("HEAD"))
    private static void crosstie$ensureGLStateManagerLoadedOnClientThread(CallbackInfo ci) {
        // GLStateManager クラスをクライアントスレッド（GLコンテキスト有効）で強制ロードする。
        // これにより静的初期化時の MAX_TEXTURE_UNITS クエリが有効なコンテキスト上で実行される。
        //
        // noinspection ResultOfMethodCallIgnored
        GLStateManager.isSplashComplete();
    }
}
