package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityLargeRailCore.class)
public abstract class TileEntityLargeRailCoreInvalidateMixin extends TileEntity {
    @Inject(method = "invalidate", at = @At("HEAD"), remap = false)
    private void crosstie$onInvalidate(CallbackInfo ci) {
        this.tileEntityInvalid = true;
    }
}
