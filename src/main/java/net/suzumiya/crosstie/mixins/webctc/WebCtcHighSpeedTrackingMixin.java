package net.suzumiya.crosstie.mixins.webctc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Fixes WebCTC train tracking failures at high speeds (e.g., 160 km/h).
 *
 * <p>Increases RailGroup update frequency to every tick.
 * Performance impact is mitigated by existing CrossTie optimizations.
 */
@Mixin(targets = "org.webctc.WebCTCEventHandler", remap = false)
public abstract class WebCtcHighSpeedTrackingMixin {

    /**
     * Replaces the 20-tick update interval constant with 1 tick.
     * require = 0 to prevent crash if the method signature differs on some environments.
     */
    @ModifyConstant(method = "onServerTick", constant = @Constant(intValue = 20), remap = false, require = 0)
    private int crosstie$forceEveryTickUpdate(int original) {
        return 1;
    }
}
