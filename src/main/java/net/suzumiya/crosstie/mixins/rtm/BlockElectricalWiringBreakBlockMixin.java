package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.BlockElectricalWiring;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockElectricalWiring.class)
public abstract class BlockElectricalWiringBreakBlockMixin extends BlockContainer {
    
    protected BlockElectricalWiringBreakBlockMixin(Material material) {
        super(material);
    }

    /**
     * @author Suzumiya
     * @reason MCTE paste class cast exception fix
     */
    @Overwrite
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityElectricalWiring) {
            ((TileEntityElectricalWiring) tile).onBlockBreaked();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
