package net.suzumiya.crosstie.mixins.macros;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.eq2online.macros.input.InputHandler", remap = false)
public abstract class MacroInputHandlerMixin {

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void crosstie$initMissingSneakKeyBinding(CallbackInfo ci) {
        try {
            Class<?> inputHandlerClass = Class.forName(
                    "net.eq2online.macros.input.InputHandler",
                    false,
                    MacroInputHandlerMixin.class.getClassLoader());
            Field keybindSneak = inputHandlerClass.getDeclaredField("keybindSneak");
            keybindSneak.setAccessible(true);
            if (keybindSneak.get(null) != null) {
                return;
            }

            Field keybindActivate = inputHandlerClass.getDeclaredField("keybindActivate");
            keybindActivate.setAccessible(true);
            Object activateBinding = keybindActivate.get(null);
            Object disabledSneakBinding = createDisabledKeyBinding(activateBinding);
            if (disabledSneakBinding != null) {
                keybindSneak.set(null, disabledSneakBinding);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Object createDisabledKeyBinding(Object referenceBinding) throws ReflectiveOperationException {
        if (referenceBinding == null) {
            return null;
        }

        Constructor<?> constructor = referenceBinding.getClass()
                .getDeclaredConstructor(String.class, int.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance("key.macro_modifier", 0, "key.categories.macros");
    }
}
