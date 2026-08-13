package net.suzumiya.crosstie.mixins.journeymap;

import journeymap.client.render.backend.WorldRenderContext;
import journeymap.client.render.ingame.WaypointBeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

@Mixin(value = WaypointBeaconRenderer.class, remap = false)
public class WaypointBeaconRendererMixin {

    @Inject(method = "render(Ljourneymap/client/render/backend/WorldRenderContext;)V", at = @At("TAIL"))
    private void crosstie$resetColorAfterRender(WorldRenderContext wc, CallbackInfo ci) {
        // Reset color state to prevent waypoint colors from leaking to other GUIs (e.g. RTM GUI)
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
