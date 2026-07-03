package net.suzumiya.crosstie.mixins.worldedit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.sk89q.worldedit.blocks.BaseBlock", remap = false)
public class MixinBaseBlock {
    @Shadow
    private short id;

    /**
     * @author Suzumiya (CrossTie)
     * @reason Overrides the 4095 hardcoded ID limit to support extended block IDs up to 32767 for NEID compatibility.
     */
    @Overwrite
    protected final void internalSetId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Can't have a block ID below 0");
        }
        if (id > 32767) {
            throw new IllegalArgumentException("Can't have a block ID above 32767 (" + id + " given)");
        }
        this.id = (short) id;
    }
}
