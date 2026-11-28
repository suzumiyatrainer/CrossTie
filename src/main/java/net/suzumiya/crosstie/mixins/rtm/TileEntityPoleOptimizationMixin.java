package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.RTMBlock;
import jp.ngt.rtm.block.tileentity.TileEntityOrnament;
import jp.ngt.rtm.block.tileentity.TileEntityPole;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 3-5: {@link TileEntityPole} は親 {@link TileEntityOrnament} の {@code canUpdate() = false} を継承し、
 * 旧データ互換の空モデル名補完のみ {@link #validate()} で 1 回実行する。
 */
@Mixin(TileEntityPole.class)
public abstract class TileEntityPoleOptimizationMixin extends TileEntityOrnament {

    @Override
    public void validate() {
        super.validate();
        if (this.worldObj != null) {
            String current = this.getModelName();
            if (current == null || current.isEmpty()) {
                int meta = this.getBlockMetadata();
                Block block = this.getBlockType();
                if (block == RTMBlock.linePole) {
                    this.setModelName(TileEntityPole.getFixedModelName(meta));
                } else if (block == RTMBlock.framework) {
                    this.setModelName("IronFrame01");
                    this.getResourceState().color = MapColor.getMapColorForBlockColored(meta).colorValue;
                }
            }
        }
    }
}
