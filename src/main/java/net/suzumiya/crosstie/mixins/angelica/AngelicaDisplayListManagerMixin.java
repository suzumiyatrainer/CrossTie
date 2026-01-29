package net.suzumiya.crosstie.mixins.angelica;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica DisplayListManagerへのMixin。
 * 
 * hi03ExpressRailwayコンテキストがアクティブな場合、
 * Angelicaのディスプレイリストシステム全体をバイパスして
 * 直接OpenGLネイティブのディスプレイリストを使用します。
 * 
 * これによりAngelicaのVBO変換が引き起こすジオメトリ消失問題を完全に回避します。
 */
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.DisplayListManager", remap = false)
public class AngelicaDisplayListManagerMixin {

    /**
     * hi03ExpressRailwayの場合、Angelicaのディスプレイリストシステムをバイパスして
     * 直接OpenGLのglNewListを呼ぶ
     */
    @Inject(method = "glNewList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassNewListForHi03(int list, int mode, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // Angelicaをバイパスして直接OpenGLのディスプレイリストを使用
            GL11.glNewList(list, mode);
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(true);
            ci.cancel();
        }
    }

    /**
     * hi03ExpressRailwayの場合、直接OpenGLのglEndListを呼ぶ
     */
    @Inject(method = "glEndList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassEndListForHi03(CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            // Angelicaをバイパスして直接OpenGLのglEndListを呼ぶ
            GL11.glEndList();
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
            ci.cancel();
        }
    }

    /**
     * hi03ExpressRailwayのレンダリング中はisRecording()をfalseに偽装
     * これによりglBegin/glEnd等がImmediateModeRecorderをバイパスする
     */
    @Inject(method = "isRecording", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassRecordingForHi03(CallbackInfoReturnable<Boolean> cir) {
        if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            cir.setReturnValue(false);
        }
    }
}
