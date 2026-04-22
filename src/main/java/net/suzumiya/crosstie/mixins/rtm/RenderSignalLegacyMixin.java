package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.util.RenderSignalHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "jp.ngt.rtm.render.RenderSignal", remap = false)
public abstract class RenderSignalLegacyMixin {

    @Inject(method = { "renderTileEntityAt", "func_147500_a" }, at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$applySignalShaderFixFlags(TileEntity tileEntity, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
        RenderSignalHooks.onRenderStart(tileEntity, x, y, z);
    }

    @Inject(method = { "renderTileEntityAt", "func_147500_a" }, at = @At("RETURN"), remap = false, require = 0)
    private void crosstie$clearSignalShaderFixFlags(TileEntity tileEntity, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
        RenderSignalHooks.onRenderEnd();
    }
}
