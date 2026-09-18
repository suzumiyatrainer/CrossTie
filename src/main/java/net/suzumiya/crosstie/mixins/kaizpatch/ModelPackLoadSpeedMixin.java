package net.suzumiya.crosstie.mixins.kaizpatch;

import jp.ngt.rtm.modelpack.ModelPackLoadThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModelPackLoadThread.class, remap = false)
public abstract class ModelPackLoadSpeedMixin {

    @Redirect(
        method = "loadModelFromConfig",
        at = @At(
            value = "FIELD",
            target = "Ljp/ngt/rtm/RTMConfig;loadSpeed:I"
        )
    )
    private int crosstie$forceMultithreadLoadSpeed() {
        int original = jp.ngt.rtm.RTMConfig.loadSpeed;
        // If loadSpeed is 0 or 1, force it to 2 (multithreaded fixed pool)
        // 3 is WorkStealingPool, which is also fine.
        return (original <= 1) ? 2 : original;
    }
}
