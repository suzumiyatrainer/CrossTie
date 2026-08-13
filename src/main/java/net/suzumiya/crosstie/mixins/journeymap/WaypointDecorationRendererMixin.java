package net.suzumiya.crosstie.mixins.journeymap;

import journeymap.client.render.backend.RenderContext;
import journeymap.client.render.ingame.WaypointDecorationRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

@Mixin(value = WaypointDecorationRenderer.class, remap = false)
public class WaypointDecorationRendererMixin {

    @Inject(method = "render(Ljourneymap/client/render/backend/RenderContext;)V", at = @At("TAIL"))
    private void crosstie$resetColorAfterRender(RenderContext ctx, CallbackInfo ci) {
        // Reset color state to prevent waypoint icon/label colors from leaking to other GUIs (e.g. RTM GUI)
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
