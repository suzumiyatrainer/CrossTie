package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.nbt.NBTTagCompound;

@Mixin(value = TileEntityLargeRailCore.class, remap = false)
public abstract class TileEntityRailPropertyMutationDiagnosticMixin {

    @Inject(method = "readRailProperties(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"))
    private void onReadRailProperties(NBTTagCompound nbt, CallbackInfo ci) {
        if (!nbt.hasKey("Property")) {
            new Throwable("[CrossTie-DIAG] readRailProperties called but Property is missing in NBT! railShape="
                    + nbt.getByte("railShape")).printStackTrace();
        }
    }
}
