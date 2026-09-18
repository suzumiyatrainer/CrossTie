package net.suzumiya.crosstie.mixins.rtm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.kaiz.kaizpatch.rtm.rail.util.RailMapSection;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.render.RailRenderSamplePlan;
import jp.ngt.rtm.render.RailRenderSampling;

@Mixin(value = RailRenderSampling.class, remap = false)
public class RailRenderSamplingMixin {

    @Inject(method = "createRailRenderSamplePlan(Ljp/ngt/rtm/rail/util/RailMap;I)Ljp/ngt/rtm/render/RailRenderSamplePlan;", at = @At("HEAD"), cancellable = true)
    private static void onCreateRailRenderSamplePlan(RailMap railMap, int minimumSplit, CallbackInfoReturnable<RailRenderSamplePlan> cir) {
        RailMap source = railMap;
        if (railMap instanceof RailMapSection) {
            source = ((RailMapSection) railMap).getSource();
        }

        int split = Math.max(crosstie$railRenderSplit(source.getLength()), minimumSplit);

        if (!(railMap instanceof RailMapSection)) {
            cir.setReturnValue(new RailRenderSamplePlan(source, split, 0, split + 1, false));
            return;
        }

        RailMapSection section = (RailMapSection) railMap;
        int startIndex = crosstie$firstIndexAtOrAfter(section.getStartRatio(), split);
        int endIndexExclusive;
        if (section.getEndRatio() >= 1.0) {
            endIndexExclusive = split + 1;
        } else {
            // FIX: Add 1 here to include the boundary segment so there is no gap
            endIndexExclusive = crosstie$firstIndexAtOrAfter(section.getEndRatio(), split) + 1;
        }

        cir.setReturnValue(new RailRenderSamplePlan(source, split, startIndex, endIndexExclusive, true));
    }

    private static int crosstie$railRenderSplit(double length) {
        return Math.max(0, (int) ((float) length * 2.0F));
    }

    private static int crosstie$firstIndexAtOrAfter(double ratio, int split) {
        double boundedRatio = Math.max(0.0, Math.min(1.0, ratio));
        int index = (int) Math.ceil(boundedRatio * split);
        return Math.max(0, Math.min(split, index));
    }
}
