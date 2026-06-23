package net.suzumiya.crosstie.mixins.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.RenderGlobal;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// LWJGLから定数をインポート
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;

@Mixin(value = RenderGlobal.class, priority = 500)
public abstract class AngelicaRenderDistanceWaterFixMixin {

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void crosstie$beforeRenderWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!CrossTieConfig.fixAngelicaWaterRenderDistance) {
            return;
        }
        if (Iris.enabled) {
            GLStateManager.glEnable(GL_CULL_FACE);
        }
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void crosstie$afterRenderWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!CrossTieConfig.fixAngelicaWaterRenderDistance) {
            return;
        }
        if (Iris.enabled) {
            GLStateManager.glDisable(GL_CULL_FACE);
        }
    }
}