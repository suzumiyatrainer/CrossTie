package net.suzumiya.crosstie.mixins.lwjgl3ify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.suzumiya.crosstie.compat.lwjgl3ify.ImeCompat;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link GuiScreen} 側でマウスクリック位置記録と IME tick 呼び出しを行う。
 * また、{@code handleKeyboardInput} において SDL TextInput 起因の物理キー押下文字入力を
 * マスクすることで、SANTAM や RTM を含むすべての Mod の GUI に対して一括で二重入力を防止する。
 */
@Mixin(GuiScreen.class)
public class GuiScreenImeMixin {

    @Inject(method = {"handleMouseInput", "func_146274_d"}, at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$recordMouseClick(CallbackInfo ci) {
        if (Mouse.getEventButtonState()) {
            ImeCompat.onClick(Mouse.getEventX(), Mouse.getEventY());
        }
    }

    @Inject(method = {"drawScreen", "func_73863_a"}, at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$updateImeAreaAfterDraw(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ImeCompat.tick();
    }

    @Inject(method = {"handleKeyboardInput", "func_146282_l"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void crosstie$filterKeyboardInput(CallbackInfo ci) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        char c = Keyboard.getEventCharacter();
        int k = Keyboard.getEventKey();
        char filtered = ImeCompat.filterKeyTypedChar(c, k);
        if (filtered != c) {
            ci.cancel();
            ImeCompat.dispatchKeyTyped((GuiScreen) (Object) this, filtered, k);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.func_152348_aa();
            }
        }
    }
}
