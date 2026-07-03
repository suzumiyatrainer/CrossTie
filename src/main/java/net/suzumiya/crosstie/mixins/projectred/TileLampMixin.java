package net.suzumiya.crosstie.mixins.projectred;

import mrtjp.projectred.illumination.TileLamp;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = TileLamp.class, remap = false)
public class TileLampMixin {
    
    /**
     * Forge 1.7.10 TileEntities return true by default for canUpdate(), causing them to tick every frame.
     * TileLamp has no updateEntity() method, so ticking it is pure overhead.
     * Returning false prevents the TileEntity from being added to the tick list, reducing lag drastically.
     */
    public boolean canUpdate() {
        return false;
    }
}
