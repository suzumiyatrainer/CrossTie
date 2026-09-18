package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.BlockLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
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
     * @reason MCTE paste class cast exception fix & KaizPatchX 1.10.3 TileEntityLargeRailSectionCore 対応
     *
     * <p>KaizPatchX 1.10.3 では、チャンク境界を越えるロングレールが複数の
     * {@code TileEntityLargeRailSectionCore} に自動分割される。
     * {@code breakLogicalRail()} は {@code TileEntityLargeRailSectionCore} で override されており、
     * グループ全体（複数チャンクにまたがる全 SectionCore）を走査して正しく破壊する。
     * 旧来の手動 RailMap ループでは SectionCore グループの他 Core を破壊できない。
     */
    @Overwrite
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLargeRailBase) {
            TileEntityLargeRailBase tile0 = (TileEntityLargeRailBase) te;
            TileEntityLargeRailCore core = tile0.getRailCore();
            if (!world.isRemote && core != null && !core.breaking) {
                core.breaking = true;
                // KaizPatchX 1.10.3: TileEntityLargeRailSectionCore の breakLogicalRail() が
                // グループ全体のチャンク分割レールをまとめて破壊する。
                // 通常の TileEntityLargeRailCore では元の RailMap ループと同等の動作をする。
                core.breakLogicalRail();
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
