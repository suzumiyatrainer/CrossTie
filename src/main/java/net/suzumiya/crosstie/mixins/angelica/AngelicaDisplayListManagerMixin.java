package net.suzumiya.crosstie.mixins.angelica;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica の DisplayListManager に対する hi03ExpressRailway 用の分岐を追加する。
 *
 * hi03ExpressRailway の描画中は、Angelica のディスプレイリスト処理をバイパスして
 * 直接 OpenGL の古い経路を使います。これで VBO 変換によるジオメトリ崩れを防ぎます。
 */
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.DisplayListManager", remap = false)
public class AngelicaDisplayListManagerMixin {

    /**
     * hi03ExpressRailway の描画中は、Angelica 経由ではなく直接 OpenGL の glNewList を呼ぶ。
     */
    @Inject(method = "glNewList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassNewListForHi03(int list, int mode, CallbackInfo ci) {
        if (!Hi03ExpressRailwayContext.isActive() && Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
        }

        if (Hi03ExpressRailwayContext.isActive()) {
            // Angelica を経由せず、直接 OpenGL のディスプレイリストを使う
            GL11.glNewList(list, mode);
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(true);
            ci.cancel();
        }
    }

    /**
     * hi03ExpressRailway の描画中は、OpenGL の glEndList を直接呼ぶ。
     */
    @Inject(method = "glEndList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassEndListForHi03(CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList() && Hi03ExpressRailwayContext.isActive()) {
            // Angelica を経由せず、直接 OpenGL の glEndList を呼ぶ
            GL11.glEndList();
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
            ci.cancel();
        } else if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
        }
    }

    /**
     * hi03ExpressRailway の描画中は isRecording() を false にする。
     *
     * これにより glBegin / glEnd 系の処理が ImmediateModeRecorder に横取りされなくなる。
     */
    @Inject(method = "isRecording", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassRecordingForHi03(CallbackInfoReturnable<Boolean> cir) {
        if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList() && Hi03ExpressRailwayContext.isActive()) {
            cir.setReturnValue(false);
        } else if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
        }
    }
}
