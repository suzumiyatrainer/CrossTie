package net.suzumiya.crosstie.mixins.macros;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.eq2online.macros.core.MacroModCore", remap = false)
public abstract class MacroModCoreMixin {

    @Inject(
            method = "onTickInGame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/eq2online/macros/gui/helpers/HelperUserSkinDownload;__displayTexture()V"),
            cancellable = true,
            remap = false)
    private void crosstie$skipMissingSkinManagerDisplay(CallbackInfo ci) {
        if (getUserSkinManager() == null) {
            ci.cancel();
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
