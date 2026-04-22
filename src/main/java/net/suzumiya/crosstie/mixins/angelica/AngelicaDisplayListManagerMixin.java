package net.suzumiya.crosstie.mixins.angelica;

import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.AngelicaRenderGuard;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * レガシー display list に依存する描画パスで Angelica の VBO 変換レコーダーをバイパスする。
 *
 * <p>対象コンテキスト:
 * <ul>
 *   <li><b>Hi03ExpressRailway</b>: hi03 レールは GLHelper.startCompile で
 *       GL_COMPILE_AND_EXECUTE に昇格済み（GLHelperMixin）。glNewList はネイティブ GL を使う。</li>
 *   <li><b>MCTEMiniature</b>: ミニチュアの renderBlocks は private メソッドのため @Inject 不可。
 *       GLHelperMixin が startCompile を GL_COMPILE_AND_EXECUTE に昇格し、glNewList を
 *       ネイティブ GL で呼ぶ。Angelica のレコーダーをバイパスすることで VBO 変換による
 *       ジオメトリ崩れを防ぐ。</li>
 * </ul>
 *
 * <p>設計方針: compile/replay の境界である glNewList/glEndList/glCallList/isRecording だけをバイパスする。
 * 個別の GL コマンド（glTranslate 等）は Angelica が録画中でないため自動的にネイティブ実行される。
 */
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.DisplayListManager", remap = false)
public class AngelicaDisplayListManagerMixin {

    /** アクティブなコンテキストのうち、バイパスが必要かどうか */
    private static boolean crosstie$shouldBypass() {
        return Hi03ExpressRailwayContext.isActive() || McteMiniatureRenderContext.isActive();
    }

    /** アクティブなコンテキストでレガシー display list を使用中かどうか */
    private static boolean crosstie$isUsingLegacy() {
        return (Hi03ExpressRailwayContext.isActive() && Hi03ExpressRailwayContext.isUsingLegacyDisplayList())
                || (McteMiniatureRenderContext.isActive() && McteMiniatureRenderContext.isUsingLegacyDisplayList());
    }

    /** アクティブなコンテキストの legacyDisplayList フラグを設定 */
    private static void crosstie$setUsingLegacy(boolean using) {
        if (Hi03ExpressRailwayContext.isActive()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(using);
        }
        if (McteMiniatureRenderContext.isActive()) {
            McteMiniatureRenderContext.setUsingLegacyDisplayList(using);
        }
    }

    /**
     * 非アクティブなのに legacyDisplayList が立ったままのケースをクリアする。
     * 例外処理等でコンテキストが正常に exit されなかった場合の保護。
     */
    private static void crosstie$clearStaleFlags() {
        if (!Hi03ExpressRailwayContext.isActive() && Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
        }
        if (!McteMiniatureRenderContext.isActive() && McteMiniatureRenderContext.isUsingLegacyDisplayList()) {
            McteMiniatureRenderContext.setUsingLegacyDisplayList(false);
        }
    }

    @Inject(method = "glNewList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassNewList(int list, int mode, CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }
        crosstie$clearStaleFlags();
        if (!crosstie$shouldBypass()) {
            return;
        }
        GL11.glNewList(list, mode);
        crosstie$setUsingLegacy(true);
        ci.cancel();
    }

    @Inject(method = "glEndList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassEndList(CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }
        if (crosstie$isUsingLegacy()) {
            GL11.glEndList();
            crosstie$setUsingLegacy(false);
            ci.cancel();
            return;
        }
        crosstie$clearStaleFlags();
    }

    @Inject(method = "glCallList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassCallList(int list, CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }
        crosstie$clearStaleFlags();
        if (!crosstie$shouldBypass()) {
            return;
        }
        GL11.glCallList(list);
        ci.cancel();
    }

    @Inject(method = "isRecording", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassIsRecording(CallbackInfoReturnable<Boolean> cir) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }
        if (crosstie$isUsingLegacy()) {
            cir.setReturnValue(false);
            return;
        }
        crosstie$clearStaleFlags();
    }
}
