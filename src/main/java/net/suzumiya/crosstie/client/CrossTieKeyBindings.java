package net.suzumiya.crosstie.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

public class CrossTieKeyBindings {

    public static KeyBinding removeWireKey;

    public static void init() {
        removeWireKey = new KeyBinding("key.crosstie.remove_wire", Keyboard.KEY_R, "key.categories.crosstie");
        ClientRegistry.registerKeyBinding(removeWireKey);
    }
}
