package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntityMachineBase;
import jp.ngt.rtm.electric.TileEntitySignal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {TileEntityMachineBase.class, TileEntitySignal.class})
public abstract class TileEntityModelNameMutationDiagnosticMixin {

    @Inject(method = "setModelName(Ljava/lang/String;)V", at = @At("HEAD"))
    private void onSetModelName(String name, CallbackInfo ci) {
        if (name == null || name.isEmpty()) {
            new Throwable("[CrossTie-DIAG] setModelName called with empty string!").printStackTrace();
        }
    }
}
