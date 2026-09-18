package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.RailPartsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailPartsRendererScriptGuardMixin {

    @Inject(method = "renderRailDynamic", at = @At("HEAD"), cancellable = true)
    private void crosstie$guardRenderRailDynamic(jp.ngt.rtm.rail.TileEntityLargeRailCore tileEntity, double x, double y, double z, float par8, CallbackInfo ci) {
        // If the rail renderer has no script, it does not need dynamic rendering.
        // Skip the invocation entirely to save method calls and argument array allocations.
        if (((jp.ngt.rtm.render.PartsRenderer<?, ?>) (Object) this).getScript() == null) {
            ci.cancel();
        }
    }
}
