package net.suzumiya.crosstie.mixins.splash;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import cpw.mods.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SplashProgress.class, remap = false)
public class MixinSplashProgressMatrixSync {

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMatrixMode(I)V"))
    private static void redirectMatrixMode(int mode) {
        GLStateManager.glMatrixMode(mode);
    }

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadIdentity()V"))
    private static void redirectLoadIdentity() {
        GLStateManager.glLoadIdentity();
    }

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glOrtho(DDDDDD)V"))
    private static void redirectOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        GLStateManager.glOrtho(left, right, bottom, top, zNear, zFar);
    }
}