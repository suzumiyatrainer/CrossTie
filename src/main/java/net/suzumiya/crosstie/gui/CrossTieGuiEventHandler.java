package net.suzumiya.crosstie.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.suzumiya.crosstie.Tags;

public class CrossTieGuiEventHandler {

    private static final int BUTTON_ID_CROSSTIE = 133701;

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiOptions) {
            int x = event.gui.width / 2 + 5;
            int y = event.gui.height / 6 + 48 - 6;
            event.buttonList.add(new GuiButton(BUTTON_ID_CROSSTIE, x, y, 150, 20, Tags.MODNAME));
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui instanceof GuiOptions && event.button.id == BUTTON_ID_CROSSTIE) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new CrossTieConfigGui(event.gui));
            event.setCanceled(true);
            return;
        }

        if (event.gui != null && "jp.kaiz.atsassistmod.gui.GUIIFTTTMaterial".equals(event.gui.getClass().getName())) {
            handleAtsAssistIftttGui(event);
        }
    }

    private static final java.util.regex.Pattern COORD_PATTERN = java.util.regex.Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)[^\\d-]+(-?\\d+(?:\\.\\d+)?)[^\\d-]+(-?\\d+(?:\\.\\d+)?)"
    );

    @SuppressWarnings("unchecked")
    private void handleAtsAssistIftttGui(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiButton button = event.button;
        if (button == null) return;

        try {
            Class<?> guiClass = event.gui.getClass();
            java.lang.reflect.Field typeField = guiClass.getDeclaredField("type");
            typeField.setAccessible(true);
            Object type = typeField.get(event.gui);
            if (type == null) return;

            java.lang.reflect.Method getIdMethod = type.getClass().getMethod("getId");
            int typeId = ((Number) getIdMethod.invoke(type)).intValue();

            java.lang.reflect.Field ifcbField = guiClass.getDeclaredField("ifcb");
            ifcbField.setAccessible(true);
            Object ifcb = ifcbField.get(event.gui);
            if (ifcb == null) return;

            java.lang.reflect.Field textFieldListField = guiClass.getDeclaredField("textFieldList");
            textFieldListField.setAccessible(true);
            java.util.List<net.minecraft.client.gui.GuiTextField> textFieldList =
                    (java.util.List<net.minecraft.client.gui.GuiTextField>) textFieldListField.get(event.gui);

            if (typeId == 211) { // 211: PlaySound
                if (button.id == 1000 && button instanceof cpw.mods.fml.client.config.GuiCheckBox) {
                    boolean checked = ((cpw.mods.fml.client.config.GuiCheckBox) button).isChecked();
                    java.lang.reflect.Method setOnceMethod = ifcb.getClass().getMethod("setOnce", boolean.class);
                    setOnceMethod.invoke(ifcb, checked);
                    event.setCanceled(true);
                } else if (button.id == 1001) {
                    int[] pos = parseClipboardPos();
                    if (pos != null) {
                        java.lang.reflect.Method setPosMethod = ifcb.getClass().getMethod("setPos", int.class, int.class, int.class);
                        setPosMethod.invoke(ifcb, pos[0], pos[1], pos[2]);
                        if (textFieldList != null && textFieldList.size() >= 5) {
                            textFieldList.get(2).setText(String.valueOf(pos[0]));
                            textFieldList.get(3).setText(String.valueOf(pos[1]));
                            textFieldList.get(4).setText(String.valueOf(pos[2]));
                        }
                    }
                    event.setCanceled(true);
                }
            } else if (typeId == 130) { // 130: 踏切障検
                if (button.id == 1000) {
                    int[] pos = parseClipboardPos();
                    if (pos != null) {
                        java.lang.reflect.Method setStartCCMethod = ifcb.getClass().getMethod("setStartCC", int.class, int.class, int.class);
                        setStartCCMethod.invoke(ifcb, pos[0], pos[1], pos[2]);
                        if (textFieldList != null && textFieldList.size() >= 3) {
                            textFieldList.get(0).setText(String.valueOf(pos[0]));
                            textFieldList.get(1).setText(String.valueOf(pos[1]));
                            textFieldList.get(2).setText(String.valueOf(pos[2]));
                        }
                    }
                    event.setCanceled(true);
                } else if (button.id == 1001) {
                    int[] pos = parseClipboardPos();
                    if (pos != null) {
                        java.lang.reflect.Method setEndCCMethod = ifcb.getClass().getMethod("setEndCC", int.class, int.class, int.class);
                        setEndCCMethod.invoke(ifcb, pos[0], pos[1], pos[2]);
                        if (textFieldList != null && textFieldList.size() >= 6) {
                            textFieldList.get(3).setText(String.valueOf(pos[0]));
                            textFieldList.get(4).setText(String.valueOf(pos[1]));
                            textFieldList.get(5).setText(String.valueOf(pos[2]));
                        }
                    }
                    event.setCanceled(true);
                }
            }
        } catch (Throwable t) {
            // Fail-safe to avoid disrupting GUI on unexpected errors
        }
    }

    private int[] parseClipboardPos() {
        String clipboardText = net.minecraft.client.gui.GuiScreen.getClipboardString();
        if (clipboardText == null) return null;
        clipboardText = clipboardText.trim();
        if (clipboardText.isEmpty()) return null;

        java.util.regex.Matcher matcher = COORD_PATTERN.matcher(clipboardText);
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
