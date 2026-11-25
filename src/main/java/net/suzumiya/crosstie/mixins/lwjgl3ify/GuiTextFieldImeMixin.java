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
 * バニラ {@link GuiTextField} (およびそのサブクラス)の描画・フォーカス・クリック・キー入力時に
 * {@link ImeCompat} と連携し、アクティブ欄追跡と二重入力防止を行う。
 */
@Mixin(GuiTextField.class)
public class GuiTextFieldImeMixin {

    @Inject(method = {"drawTextBox", "func_146194_f"}, at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$onDrawTextBox(CallbackInfo ci) {
        ImeCompat.onTextFieldActive((GuiTextField) (Object) this);
    }

    @Inject(method = {"setFocused", "func_146195_b"}, at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$onSetFocused(boolean focused, CallbackInfo ci) {
        if (focused) {
            ImeCompat.onTextFieldActive((GuiTextField) (Object) this);
        }
    }

    @Inject(method = {"mouseClicked", "func_146192_a"}, at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$onMouseClicked(int x, int y, int button, CallbackInfo ci) {
        ImeCompat.onTextFieldActive((GuiTextField) (Object) this);
    }

    @Inject(method = {"textboxKeyTyped", "func_146201_a"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void crosstie$onTextboxKeyTypedHead(char typedChar, int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (!ImeCompat.isSdlTextInputActive()) {
            return;
        }
        if (keyCode == Keyboard.KEY_NONE) {
            ImeCompat.prepareLwjgl3ifyTextBufferForChar(typedChar);
        } else {
            ImeCompat.clearLwjgl3ifyTextBuffer();
            if (typedChar >= 32 && !GuiScreen.isCtrlKeyDown()
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
}
