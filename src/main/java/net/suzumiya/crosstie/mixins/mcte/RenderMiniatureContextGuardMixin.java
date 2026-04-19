package net.suzumiya.crosstie.mixins.mcte;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guard MCTE miniature rendering from leaked legacy hi03 context.
 * Miniature NGTO must stay on normal NGTRenderer path.
 */
@Mixin(targets = "jp.ngt.mcte.block.RenderMiniature", remap = false)
public abstract class RenderMiniatureContextGuardMixin {

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), remap = false)
    private void crosstie$resetLegacyContextBeforeMiniature(TileEntity tile, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive() || Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.reset();
        }
    }

    @Inject(method = "renderTileEntityAt", at = @At("RETURN"), remap = false)
    private void crosstie$resetLegacyContextAfterMiniature(TileEntity tile, double x, double y, double z,
            float partialTicks, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive() || Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
            Hi03ExpressRailwayContext.reset();
        }
    }
}
