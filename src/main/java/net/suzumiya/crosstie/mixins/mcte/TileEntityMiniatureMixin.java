package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Ensures MCTE miniatures are not culled by Angelica when their origin block is off-screen.
 * Refactored to be dependency-free for CI build stability.
 */
@Mixin(targets = "jp.ngt.mcte.block.TileEntityMiniature", remap = false)
public abstract class TileEntityMiniatureMixin extends TileEntity {

    /**
     * @author Antigravity
     * @reason Prevent Angelica's frustum and occlusion culling from hiding MCTE miniatures.
     */
    @Override
    @Overwrite
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
