package net.suzumiya.crosstie.mixins.lwjgl3ify;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.suzumiya.crosstie.compat.lwjgl3ify.ImeCompat;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NGTLib の {@code GuiTextFieldCustom} に対する IME 連携および二重入力防止フック。
 */
@Mixin(targets = "jp.ngt.ngtlib.gui.GuiTextFieldCustom", remap = false)
public class GuiTextFieldCustomImeMixin {

    @Inject(method = "drawTextBox()V", at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$onDrawTextBox(CallbackInfo ci) {
        ImeCompat.onTextFieldActive((GuiTextField) (Object) this);
    }

    @Inject(method = "setFocused(Z)V", at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$onSetFocused(boolean focused, CallbackInfo ci) {
        if (focused) {
            ImeCompat.onTextFieldActive((GuiTextField) (Object) this);
        }
    }

    @Inject(method = "mouseClicked(III)V", at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$onMouseClicked(int x, int y, int button, CallbackInfo ci) {
        ImeCompat.onTextFieldActive((GuiTextField) (Object) this);
    }

    @Inject(method = "textboxKeyTyped(CI)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void crosstie$onTextboxKeyTypedHead(char typedChar, int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (!ImeCompat.isSdlTextInputActive()) {
            return;
        }
        ImeCompat.clearLwjgl3ifyTextBuffer();
        if (keyCode != Keyboard.KEY_NONE && typedChar >= 32 && !GuiScreen.isCtrlKeyDown()
            && keyCode != Keyboard.KEY_BACK
            && keyCode != Keyboard.KEY_HOME
            && keyCode != Keyboard.KEY_LEFT
            && keyCode != Keyboard.KEY_RIGHT
            && keyCode != Keyboard.KEY_END
            && keyCode != Keyboard.KEY_DELETE) {
            cir.setReturnValue(false);
        }
    }
}
