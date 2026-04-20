package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "jp.ngt.mcte.block.TileEntityMiniature", remap = false)
public abstract class TileEntityMiniatureMixin extends TileEntity {

    /**
     * @author Antigravity
     * @reason Prevent Angelica's frustum and occlusion culling from hiding MCTE miniatures.
     * 
     * Root Cause: MCTE miniatures can be much larger than their 1x1x1 anchor block.
     * Angelica caches the bounding boxes of TileEntities and uses them for culling.
     * If the anchor block goes off-screen, Angelica may cull the entire miniature
     * even if its extended features are still visible.
     * 
     * By returning INFINITE_EXTENT_AABB, we ensure that Angelica always considers
     * the miniature as "potentially visible" and executes its rendering code,
     * letting the normal GL frustum handle the actual pixel-level clipping.
     */
    @Override
    @Overwrite
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
