package net.suzumiya.crosstie.mixins.angelica;

import com.gtnewhorizons.angelica.rendering.FpsReducer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FpsReducer.class, remap = false)
public class FpsReducerMixin {
    private static boolean hasRenderedSinceWorldLoad = false;
    private static int lastWorldHash = 0;

    @Inject(method = "onRenderSkipped", at = @At("HEAD"), cancellable = true)
    private static void onRenderSkippedInject(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            int currentWorldHash = System.identityHashCode(mc.theWorld);
            if (currentWorldHash != lastWorldHash) {
                lastWorldHash = currentWorldHash;
                hasRenderedSinceWorldLoad = false;
            }
            if (!hasRenderedSinceWorldLoad) {
                // Prevent pause menu opening if we haven't rendered a frame yet since world load
                ci.cancel();
            }
        } else {
            lastWorldHash = 0;
            hasRenderedSinceWorldLoad = false;
        }
    }

    @Inject(method = "evaluateFrame", at = @At("TAIL"))
    private static void onEvaluateFrame(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && !FpsReducer.skipRender()) {
            hasRenderedSinceWorldLoad = true;
        }
    }
}
