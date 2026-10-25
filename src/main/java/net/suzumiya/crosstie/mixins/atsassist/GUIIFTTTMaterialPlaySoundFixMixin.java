package net.suzumiya.crosstie.mixins.atsassist;

import cpw.mods.fml.client.config.GuiCheckBox;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ATSAssistMod の GUIIFTTTMaterial におけるバグ修正Mixin:
 * 1) 「一度のみ再生」チェックボックス (button.id == 1000) が押せない問題の修正:
 *    元の actionPerformed に case 211 (PlaySound) の case 1000 処理が抜け落ちており、
 *    毎フレーム drawScreen で isOnce() に上書きされて状態が反映されなかった。
 * 2) 座標貼り付け「V」ボタン (button.id == 1001) が押せない/反映されない問題の修正:
 *    元の actionPerformed では PlaySound#setPos() のみを呼んでおり、画面上の textFieldList (X, Y, Z)
 *    を更新していなかったため、画面に反映されず、さらに「追加」ボタン押下時に画面のテキストで上書きされていた。
 *    またクリップボードの座標パース処理 (getPosFromClipboard) の正規表現不備 (負数やカンマ区切り、余分な空白対応) を改善。
 *
 * 注: ATSAssistMod は Jar-in-Jar 形式 (core.jar.lzma) でパッケージされており、
 * コンパイル時の直接依存を避けるため、type および ifcb の操作はリフレクション経由で安全に行う。
 */
@Mixin(targets = "jp.kaiz.atsassistmod.gui.GUIIFTTTMaterial", remap = false)
public abstract class GUIIFTTTMaterialPlaySoundFixMixin extends GuiScreen {

    @Shadow
    private List<GuiTextField> textFieldList;

    @Shadow
    private Object type;

    @Shadow
    private Object ifcb;

    @Unique
    private static final Pattern CROSSTIE$COORD_PATTERN = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)[^\\d-]+(-?\\d+(?:\\.\\d+)?)[^\\d-]+(-?\\d+(?:\\.\\d+)?)"
    );

    @Inject(
            method = { "actionPerformed", "func_146284_a" },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void crosstie$onActionPerformedPlaySound(GuiButton button, CallbackInfo ci) {
        if (button == null || this.type == null) {
            return;
        }

        int typeId = crosstie$getTypeId(this.type);

        // 211: PlaySound
        if (typeId == 211 && this.ifcb != null) {
            if (button.id == 1000 && button instanceof GuiCheckBox) {
                // 「一度のみ再生」チェックボックス
                boolean checked = ((GuiCheckBox) button).isChecked();
                crosstie$setOnce(this.ifcb, checked);
                ci.cancel();
            } else if (button.id == 1001) {
                // 座標貼り付け「V」ボタン
                int[] pos = crosstie$getPosFromClipboard();
                if (pos != null) {
                    crosstie$setPos(this.ifcb, pos[0], pos[1], pos[2]);
                    if (this.textFieldList != null && this.textFieldList.size() >= 5) {
                        this.textFieldList.get(2).setText(String.valueOf(pos[0]));
                        this.textFieldList.get(3).setText(String.valueOf(pos[1]));
                        this.textFieldList.get(4).setText(String.valueOf(pos[2]));
                    }
                }
                ci.cancel();
            }
        } else if (typeId == 130 && this.ifcb != null) {
            // 130: 踏切障検 (同様にtextFieldListが更新されないバグを予防的修正)
            if (button.id == 1000) {
                int[] pos = crosstie$getPosFromClipboard();
                if (pos != null) {
                    crosstie$setStartCC(this.ifcb, pos[0], pos[1], pos[2]);
                    if (this.textFieldList != null && this.textFieldList.size() >= 3) {
                        this.textFieldList.get(0).setText(String.valueOf(pos[0]));
                        this.textFieldList.get(1).setText(String.valueOf(pos[1]));
                        this.textFieldList.get(2).setText(String.valueOf(pos[2]));
                    }
                }
                ci.cancel();
            } else if (button.id == 1001) {
                int[] pos = crosstie$getPosFromClipboard();
                if (pos != null) {
                    crosstie$setEndCC(this.ifcb, pos[0], pos[1], pos[2]);
                    if (this.textFieldList != null && this.textFieldList.size() >= 6) {
                        this.textFieldList.get(3).setText(String.valueOf(pos[0]));
                        this.textFieldList.get(4).setText(String.valueOf(pos[1]));
                        this.textFieldList.get(5).setText(String.valueOf(pos[2]));
                    }
                }
                ci.cancel();
            }
        }
    }

    @Unique
    private static int crosstie$getTypeId(Object typeObj) {
        if (typeObj == null) {
            return -1;
        }
        try {
            Method m = typeObj.getClass().getMethod("getId");
            Object result = m.invoke(typeObj);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    @Unique
    private static void crosstie$setOnce(Object container, boolean once) {
        if (container == null) {
            return;
        }
        try {
            Method m = container.getClass().getMethod("setOnce", boolean.class);
            m.invoke(container, once);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void crosstie$setPos(Object container, int x, int y, int z) {
        if (container == null) {
            return;
        }
        try {
            Method m = container.getClass().getMethod("setPos", int.class, int.class, int.class);
            m.invoke(container, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void crosstie$setStartCC(Object container, int x, int y, int z) {
        if (container == null) {
            return;
        }
        try {
            Method m = container.getClass().getMethod("setStartCC", int.class, int.class, int.class);
            m.invoke(container, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void crosstie$setEndCC(Object container, int x, int y, int z) {
        if (container == null) {
            return;
        }
        try {
            Method m = container.getClass().getMethod("setEndCC", int.class, int.class, int.class);
            m.invoke(container, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private int[] crosstie$getPosFromClipboard() {
        String clipboardText = GuiScreen.getClipboardString();
        if (clipboardText == null) {
            return null;
        }
        clipboardText = clipboardText.trim();
        if (clipboardText.isEmpty()) {
            return null;
        }

        Matcher matcher = CROSSTIE$COORD_PATTERN.matcher(clipboardText);
        if (matcher.find()) {
            try {
                int x = (int) Math.floor(Double.parseDouble(matcher.group(1)));
                int y = (int) Math.floor(Double.parseDouble(matcher.group(2)));
                int z = (int) Math.floor(Double.parseDouble(matcher.group(3)));
                return new int[] { x, y, z };
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
