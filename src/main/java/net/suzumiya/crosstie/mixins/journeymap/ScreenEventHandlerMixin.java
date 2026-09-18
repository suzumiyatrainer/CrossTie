package net.suzumiya.crosstie.mixins.journeymap;

import journeymap.client.event.handlers.ScreenEventHandler;
import journeymap.client.render.backend.RenderBackends;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JourneyMap の ScreenEventHandler.onScreenMousePressedEvent が、
 * FML の GuiDupesFound 等の初期化前画面に対して呼ばれた際に
 * RenderBackends.get() が null を返し NPE が発生するバグを修正する。
 *
 * <p>原因: allowableDragScreens() が GuiDupesFound 等に対して誤って true を返すため、
 * 未初期化の RenderBackend に対してアクセスしてしまう。
 * RenderBackends.get() の返り値が null の場合は早期 return して NPE を防ぐ。
 */
@Mixin(value = ScreenEventHandler.class, remap = false)
public abstract class ScreenEventHandlerMixin {

    /**
     * onScreenMousePressedEvent の先頭で RenderBackends が初期化済みかを確認し、
     * null の場合は false を返して早期終了する。
     */
    @Inject(
        method = "onScreenMousePressedEvent",
        at = @At("HEAD"),
        cancellable = true
    )
    private void crosstie$guardRenderBackendNull(
            GuiScreen screen, double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RenderBackends.get() == null) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            // RenderBackends 自体が初期化されていない場合も安全に握りつぶす
            cir.setReturnValue(false);
        }
    }
}