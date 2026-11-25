package net.suzumiya.crosstie.mixins.lwjgl3ify;

import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code org.lwjglx.input.Keyboard} は lwjgl3ify 側で transformer 除外されているため、
 * 実際のキーイベント/文字入力制御は {@link GuiScreenImeMixin}, {@link GuiTextFieldImeMixin},
 * {@link GuiTextFieldCustomImeMixin} 側で行う。互換性のためクラスのみ維持する。
 */
@Mixin(targets = "org.lwjglx.input.Keyboard", remap = false)
public class Lwjgl3ifyImeKeyboardMixin {
}
