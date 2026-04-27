package net.suzumiya.crosstie.mixins.macros;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    private void crosstie$displayTextureIfSkinManagerExists(Object userSkinManager) {
        if (userSkinManager == null) {
            return;
        }

        try {
            Method displayTexture = userSkinManager.getClass().getDeclaredMethod("__displayTexture");
            displayTexture.setAccessible(true);
            displayTexture.invoke(userSkinManager);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }
}
