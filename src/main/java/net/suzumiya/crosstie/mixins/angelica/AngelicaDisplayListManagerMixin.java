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
 * Bypass Angelica's display-list recorder for render paths that still depend on native legacy lists.
 */
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.DisplayListManager", remap = false)
public class AngelicaDisplayListManagerMixin {

    private static void crosstie$clearStaleLegacyFlags() {
        if (!Hi03ExpressRailwayContext.isActive() && Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(false);
        }
        if (!McteMiniatureRenderContext.isActive() && McteMiniatureRenderContext.isUsingLegacyDisplayList()) {
            McteMiniatureRenderContext.setUsingLegacyDisplayList(false);
        }
    }

    private static boolean crosstie$shouldBypassDisplayListManager() {
        return Hi03ExpressRailwayContext.isActive() || McteMiniatureRenderContext.isActive();
    }

    private static void crosstie$setUsingLegacyDisplayList(boolean using) {
        if (Hi03ExpressRailwayContext.isActive()) {
            Hi03ExpressRailwayContext.setUsingLegacyDisplayList(using);
        }
        if (McteMiniatureRenderContext.isActive()) {
            McteMiniatureRenderContext.setUsingLegacyDisplayList(using);
        }
    }

    private static boolean crosstie$isUsingLegacyDisplayListInActiveContext() {
        return (Hi03ExpressRailwayContext.isActive() && Hi03ExpressRailwayContext.isUsingLegacyDisplayList())
                || (McteMiniatureRenderContext.isActive() && McteMiniatureRenderContext.isUsingLegacyDisplayList());
    }

    @Inject(method = "glNewList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassNewListForLegacyContexts(int list, int mode, CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }

        crosstie$clearStaleLegacyFlags();
        if (!crosstie$shouldBypassDisplayListManager()) {
            return;
        }

        GL11.glNewList(list, mode);
        crosstie$setUsingLegacyDisplayList(true);
        ci.cancel();
    }

    @Inject(method = "glEndList", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassEndListForLegacyContexts(CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }

        if (crosstie$isUsingLegacyDisplayListInActiveContext()) {
            GL11.glEndList();
            crosstie$setUsingLegacyDisplayList(false);
            ci.cancel();
            return;
        }

        crosstie$clearStaleLegacyFlags();
    }

    @Inject(method = "isRecording", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$bypassRecordingForLegacyContexts(CallbackInfoReturnable<Boolean> cir) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive()) {
            return;
        }

        if (crosstie$isUsingLegacyDisplayListInActiveContext()) {
            cir.setReturnValue(false);
            return;
        }

        crosstie$clearStaleLegacyFlags();
    }
}
