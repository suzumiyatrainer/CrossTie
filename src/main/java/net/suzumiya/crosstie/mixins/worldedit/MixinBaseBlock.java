package net.suzumiya.crosstie.mixins.worldedit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.sk89q.worldedit.blocks.BaseBlock", remap = false)
public class MixinBaseBlock {

    /**
     * Overrides the hardcoded 4095 block ID limit in WorldEdit's BaseBlock#internalSetId
     * to support extended block IDs up to 32767 for NEID compatibility.
     */
    @ModifyConstant(method = "internalSetId", constant = @Constant(intValue = 4095), remap = false, require = 0)
    private int modifyMaxBlockId(int original) {
        return 32767;
    }
}
