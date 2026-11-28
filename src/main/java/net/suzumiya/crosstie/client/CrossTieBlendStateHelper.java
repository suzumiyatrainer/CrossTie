package net.suzumiya.crosstie.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;

/**
 * Phase 5-2: Angelica 2.2.47 の pass 1（{@code renderBlockEntitiesBeforeTranslucent}）において、
 * 既に {@code GL_BLEND = ON} の状態で呼ばれた TESR が末尾に無条件 {@code glDisable(GL_BLEND)} や
 * 2 引数 {@code glBlendFunc} を呼ぶことで発生する 1 TESR あたり 2 回の FBO 再バインド（スラッシング）を根絶する。
 *
 * <p>OptiFine / バニラ環境ではネイティブ {@code GL11.glIsEnabled} を一切呼ばず（ドライバストール回避）、
 * Angelica GLSM 導入時のみ Java 側キャッシュ状態（GL コール 0 回）を参照する。</p>
 */
@SideOnly(Side.CLIENT)
public final class CrossTieBlendStateHelper {

    private static final boolean ANGELICA_GLSM_AVAILABLE;
    private static final Object ANGELICA_BLEND_MODE;
    private static final Method ANGELICA_IS_ENABLED_METHOD;

    static {
        boolean available = false;
        Object blendMode = null;
        Method isEnabledMethod = null;
        try {
            Class<?> glsmClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            Method getBlendMode = glsmClass.getMethod("getBlendMode");
            blendMode = getBlendMode.invoke(null);
            if (blendMode != null) {
                isEnabledMethod = blendMode.getClass().getMethod("isEnabled");
                available = true;
            }
        } catch (Throwable ignored) {
            // Angelica 非導入環境または旧バージョン
        }
        ANGELICA_GLSM_AVAILABLE = available;
        ANGELICA_BLEND_MODE = blendMode;
        ANGELICA_IS_ENABLED_METHOD = isEnabledMethod;
    }

    private CrossTieBlendStateHelper() {
    }

    /**
     * Angelica GLSM 上で現在 {@code GL_BLEND} が既に有効かを GL コール無しで返す。
     * 非 Angelica 環境では常に {@code false} を返し、従来の有効化/無効化フローを維持する。
     */
    public static boolean isAngelicaBlendAlreadyEnabled() {
        if (!ANGELICA_GLSM_AVAILABLE) {
            return false;
        }
        try {
            return (Boolean) ANGELICA_IS_ENABLED_METHOD.invoke(ANGELICA_BLEND_MODE);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * pass 1 半透明描画の開始時に呼ぶ。
     * 既に Angelica で {@code GL_BLEND} が有効なら何もせず {@code false} を返す。
     * 無効なら {@code GL_BLEND} を有効化し 4 引数 {@code glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)} を設定して {@code true} を返す。
     */
    public static boolean enterPass1Blend() {
        if (isAngelicaBlendAlreadyEnabled()) {
            return false;
        }
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        return true;
    }

    /**
     * pass 1 半透明描画の終了時に呼ぶ。{@link #enterPass1Blend()} が {@code true} を返した場合のみ {@code GL_BLEND} を無効化する。
     */
    public static void exitPass1Blend(boolean enabledByUs) {
        if (enabledByUs) {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }
}
