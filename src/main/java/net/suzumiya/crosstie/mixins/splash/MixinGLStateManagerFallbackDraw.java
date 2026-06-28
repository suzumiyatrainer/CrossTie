package net.suzumiya.crosstie.mixins.splash;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = GLStateManager.class, remap = false)
public class MixinGLStateManagerFallbackDraw {

    @Inject(
            method = "glEnd",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/glsm/DisplayListManager;isRecording()Z",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            remap = false
    )
    private static void crosstie$drawUninitialized(CallbackInfo ci, DirectTessellator result) {
        if (result != null && GLStateManager.getInitConfig() == null && !DisplayListManager.isRecording()) {
            TessellatorStreamingDrawer.drawDirect(result);
        }
    }
}
