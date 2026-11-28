package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntitySignalConverter;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 3-5: {@link TileEntitySignalConverter} の非 Wireless サブクラス（RSIn / RSOut / Increment / Decrement）の
 * 毎 tick 空回しを廃止する Mixin。
 */
@Mixin(value = TileEntitySignalConverter.class, remap = false)
public abstract class TileEntitySignalConverterOptimizationMixin extends TileEntity {

    @Override
    public boolean canUpdate() {
        return ((Object) this) instanceof TileEntitySignalConverter.TileEntitySC_Wireless;
    }
}
