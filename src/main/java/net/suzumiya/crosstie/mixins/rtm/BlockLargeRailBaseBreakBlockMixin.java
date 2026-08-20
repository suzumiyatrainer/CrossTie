package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.BlockLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockLargeRailBase.class)
public abstract class BlockLargeRailBaseBreakBlockMixin extends BlockContainer {

    protected BlockLargeRailBaseBreakBlockMixin(Material material) {
        super(material);
    }

    /**
     * @author Suzumiya
     * @reason MCTE paste class cast exception fix & performance optimization
     */
    @Overwrite
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLargeRailBase) {
            TileEntityLargeRailBase tile0 = (TileEntityLargeRailBase) te;
            TileEntityLargeRailCore core = tile0.getRailCore();
            if (!world.isRemote && core != null && !core.breaking) {
                core.breaking = true;
                RailMap[] maps = core.getAllRailMaps();
                if (maps != null) {
                    for (int i = 0; i < maps.length; i++) {
                        RailMap rm = maps[i];
                        if (rm != null) {
                            rm.breakRail(world, core.getProperty(), core);
                        }
                    }
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
