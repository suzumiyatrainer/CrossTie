package net.suzumiya.crosstie.compat.lwjgl3ify;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/**
 * In-game diagnostics for the lwjgl3ify Keyboard.isKeyDown compatibility hook.
 */
public final class Lwjgl3ifyKeyboardDebug {
    // Flip this switch to enable or silence the in-game diagnostic messages.
    public static final boolean DEBUG_MODE = false;

    private static final Map<String, String> LAST_MESSAGE_BY_CALLER_AND_KEY = new HashMap<>();
    private static boolean hookNoticeSent;

    private Lwjgl3ifyKeyboardDebug() {
    }

    public static synchronized void report(int key, boolean result, String details) {
        if (!DEBUG_MODE) {
            return;
        }

        String caller = findScriptCaller();
        String message = "[CrossTie KeyDebug] key=" + key + " result=" + result + " caller=" + caller + " " + details;
        String dedupeKey = caller + ":" + key;
        if (message.equals(LAST_MESSAGE_BY_CALLER_AND_KEY.get(dedupeKey))) {
            return;
        }
        LAST_MESSAGE_BY_CALLER_AND_KEY.put(dedupeKey, message);
        System.out.println(message);

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return;
        }

        if (!hookNoticeSent) {
            minecraft.thePlayer
                    .addChatMessage(new ChatComponentText("§b[CrossTie KeyDebug] Keyboard.isKeyDown hook is active."));
            hookNoticeSent = true;
        }
        minecraft.thePlayer.addChatMessage(new ChatComponentText("§e" + message));
    }

    private static String findScriptCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("jdk.nashorn.internal.scripts.")
                    || className.startsWith("org.openjdk.nashorn.internal.scripts.")) {
                return className.substring(className.lastIndexOf('.') + 1) + "." + frame.getMethodName() + ":"
                        + frame.getLineNumber();
            }
        }
        return "unknown";
    }
}
