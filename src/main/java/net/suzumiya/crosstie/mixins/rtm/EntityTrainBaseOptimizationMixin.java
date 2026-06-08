package net.suzumiya.crosstie.mixins.rtm;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty mixin. Chunk loading optimizations are handled directly by KaizPatchX.
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseOptimizationMixin {
}

