package net.suzumiya.crosstie.mixins.splash;

/**
 * このクラスは廃止されました。
 *
 * 旧実装は GL11.glMatrixMode / GL11.glLoadIdentity / GL11.glOrtho を @Redirect ターゲットとして
 * いたが、AngelicaのGLSMRedirectorがバイトコードレベルでこれらを GLStateManager.* に
 * 書き換えるため、@Redirect のターゲットが存在せず MixinException でクラッシュしていた。
 *
 * スプラッシュ画面黒化の根本修正は
 * {@link MixinSplashProgressEarlyGLStateManagerLoad} で行っている。
 *
 * このクラスは JSON から除外し、CrossTieMixinPlugin.getMixins() にも追加しない。
 * ファイルは参考用として残す。
 *
 * @deprecated 使用しない
 */
@Deprecated
public class MixinSplashProgressMatrixSync {
    // 意図的に空 - このMixinは適用しない
}