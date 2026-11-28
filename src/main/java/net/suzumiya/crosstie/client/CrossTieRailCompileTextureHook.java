package net.suzumiya.crosstie.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;

/**
 * Phase 5-4: レール DisplayList コンパイル中にスクリプトから呼ばれる {@code PartsRenderer.bindTexture()} を
 * DisplayList 内部へ記録せず外側でバインドするためのフック管理。
 *
 * <p>OptiFine シェーダー ON 時に {@code ShadersTex.bindTexture}（法線/スペキュラ {@code MultiTexID}）が
 * DisplayList 内部へ固定記録されて破損する問題を防ぐ。</p>
 */
@SideOnly(Side.CLIENT)
public final class CrossTieRailCompileTextureHook {

    private static boolean capturing = false;
    private static ResourceLocation capturedTexture = null;
    private static boolean multipleTextures = false;

    private CrossTieRailCompileTextureHook() {
    }

    public static void beginCapture() {
        capturing = true;
        capturedTexture = null;
        multipleTextures = false;
    }

    /**
     * @return キャプチャ中で {@code bindTexture} の実行を抑止すべきなら {@code true}
     */
    public static boolean onBindTexture(ResourceLocation texture) {
        if (!capturing) {
            return false;
        }
        if (texture == null) {
            return true;
        }
        if (capturedTexture == null) {
            capturedTexture = texture;
        } else if (!capturedTexture.equals(texture)) {
            multipleTextures = true;
            return false;
        }
        return true;
    }

    public static ResourceLocation endCaptureGetTexture() {
        ResourceLocation result = multipleTextures ? null : capturedTexture;
        capturing = false;
        capturedTexture = null;
        return result;
    }

    public static boolean hadMultipleTextures() {
        return multipleTextures;
    }
}
