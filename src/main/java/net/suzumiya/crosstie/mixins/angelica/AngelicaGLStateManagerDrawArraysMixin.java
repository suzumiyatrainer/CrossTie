package net.suzumiya.crosstie.mixins.angelica;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hi03/NGTO 描画コンテキスト中は Angelica の glDrawArrays 変換を回避し、
 * 生の OpenGL 呼び出しへフォールバックする。
 */
@Mixin(targets = "com.gtnewhorizons.angelica.glsm.GLStateManager", remap = false)
public class AngelicaGLStateManagerDrawArraysMixin {

    @Inject(method = "glDrawArrays", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$useRawGlDrawArrays(int mode, int first, int count, CallbackInfo ci) {
        if (!Hi03ExpressRailwayContext.isActive()) {
            return;
        }

        GL11.glDrawArrays(mode, first, count);
        ci.cancel();
    }
}
