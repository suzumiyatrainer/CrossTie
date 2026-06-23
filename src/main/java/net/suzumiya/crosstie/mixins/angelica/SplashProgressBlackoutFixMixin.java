package net.suzumiya.crosstie.mixins.angelica;

import net.suzumiya.crosstie.util.SplashGLFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * enableFontRenderer=false 時のスプラッシュ画面暗転を修正する。
 *
 * <h3>暗転の原因</h3>
 * <ol>
 * <li>Angelica の {@code MixinSplashProgressCaching}(常時有効)が
 * {@code SplashProgress.start()} 内で SharedDrawable コンテキスト上に VAO を生成し、
 * GLStateManager を「スプラッシュ中の特殊モード」に切り替える。</li>
 * <li>スプラッシュ描画ループ({@code SplashProgress$3.run()})は
 * {@code SplashFontRenderer.drawString()} でテクスチャを描画しようとするが、
 * {@code enableFontRenderer=false} の場合 {@code MixinFontRenderer} が無効のため
 * {@code SplashFontRenderer}({@code FontRenderer} のサブクラス)の GL 初期化が不完全。</li>
 * <li>Angelica の ASM バイトコードリダイレクターが {@code GL11.glEnable()} を
 * {@code GLStateManager.glEnable()} に書き換え、キャッシュ更新のみで実際の
 * OpenGL コールが行われずテクスチャが一切表示されなくなる。</li>
 * </ol>
 *
 * <h3>修正方針</h3>
 * {@link SplashGLFix} を介してリフレクションで GL11 のメソッドを呼び出すことで、
 * Angelica のバイトコードリダイレクトをバイパスする。
 *
 * <p>
 * 適用条件: AngelicaGlsm が存在し かつ enableFontRenderer=false の場合のみ
 * ({@code CrossTieMixinPlugin.getMixins()} で制御)。
 */
@Mixin(targets = "cpw/mods/fml/client/SplashProgress$3", remap = false)
public class SplashProgressBlackoutFixMixin {

    /**
     * 各フレームの描画先頭で GL_TEXTURE_2D をリフレクション経由で強制有効化する。
     *
     * <p>
     * リフレクションを使用することで、Angelica のバイトコードリダイレクターによる
     * {@code GLStateManager.glEnable()} への書き換えを回避する。
     * </p>
     *
     * <p>
     * {@code require = 0} にしているため、ターゲットクラスが存在しない環境では
     * 無音でスキップされる。
     */
    @Inject(method = "run", at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$forceTextureStateForSplash(CallbackInfo ci) {
        // リフレクションで GL11.glEnable(GL_TEXTURE_2D) を呼び出し、
        // Angelica のバイトコードリダイレクトをバイパスする
        SplashGLFix.markSplashStateDirty();
    }

    /**
     * 各フレームの描画ループ内（Display.update()の直後）で GL_TEXTURE_2D を強制有効化する。
     */
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", shift = At.Shift.AFTER, remap = false), remap = false, require = 0)
    private void crosstie$forceTextureStateForSplashLoop(CallbackInfo ci) {
        SplashGLFix.markSplashStateDirty();
    }
}
