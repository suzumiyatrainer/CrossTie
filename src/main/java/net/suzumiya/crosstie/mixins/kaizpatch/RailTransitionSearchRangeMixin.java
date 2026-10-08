package net.suzumiya.crosstie.mixins.kaizpatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import jp.ngt.rtm.rail.util.RailPosition;
import kotlin.Pair;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Temporarily expands KaizPatchX's forward rail-core lookup range to five blocks. */
@Mixin(targets = "jp.kaiz.kaizpatch.rtm.rail.util.RailTransitionResolver", remap = false)
public abstract class RailTransitionSearchRangeMixin {

    /**
     * KaizPatchX's Kotlin compiler mangles this internal method name and folds
     * its 2.0 / 0.25 constants. Replacing the method keeps the test range exact.
     */
    @Overwrite(remap = false)
    public List<Pair<Integer, Integer>> forwardSearchPositions$com_yourname_modid_src1_7_10_20200822_KaizPatchX(
            RailPosition endpoint, float exitYaw) {
        int[] neighbor = endpoint.getNeighborPos();
        LinkedHashSet<Pair<Integer, Integer>> positions = new LinkedHashSet<>();
        positions.add(new Pair<>(neighbor[0], neighbor[2]));

        double yaw = Math.toRadians((double) exitYaw);
        int stepCount = (int) Math.ceil(5.0D / 0.25D);
        for (int index = 0; index < stepCount; ++index) {
            double distance = Math.min((double) (index + 1) * 0.25D, 5.0D - 1.0E-7D);
            int x = (int) Math.floor(endpoint.posX + Math.sin(yaw) * distance);
            int z = (int) Math.floor(endpoint.posZ + Math.cos(yaw) * distance);
            positions.add(new Pair<>(x, z));
        }
        return new ArrayList<>(positions);
    }
}
