package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import cpw.mods.fml.relauncher.SideOnly;
import cpw.mods.fml.relauncher.Side;

@Mixin(targets = "jp.ngt.rtm.rail.TileEntityLargeRailCore", remap = false)
public abstract class MixinTileEntityLargeRailCore {

    /**
     * @author CrossTie
     * @reason Fix origin-chunk disappearance by forcing infinite AABB, relying on Custom Aggressive Culler instead.
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    /**
     * @author CrossTie
     * @reason Disable vanilla distance cull, relying entirely on the new exact AABB Custom Culler in renderer.
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return Double.MAX_VALUE;
    }
}
