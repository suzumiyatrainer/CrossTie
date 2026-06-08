package net.suzumiya.crosstie.mixins.macros;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.eq2online.macros.core.MacroModCore", remap = false)
public abstract class MacroModCoreMixin {

    @Redirect(
            method = "onTickInGame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/eq2online/macros/gui/helpers/HelperUserSkinDownload;__displayTexture()V"),
            remap = false)
    private void crosstie$displayTextureIfSkinManagerExists(Object instance) {
        if (getUserSkinManager() != null && instance != null) {
            try {
                java.lang.reflect.Method method = instance.getClass().getDeclaredMethod("__displayTexture");
                method.setAccessible(true);
                method.invoke(instance);
            } catch (ReflectiveOperationException e) {
                // Ignore
            }
        }
    }

    private Object getUserSkinManager() {
        try {
            java.lang.reflect.Field field = this.getClass().getDeclaredField("userSkinManager");
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}

