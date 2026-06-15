package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class RenderLargeRailOptimizationMixin {

    private static final double MAX_RENDER_DIST_SQ = 192.0D * 192.0D; // 192m

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$distanceCulling(TileEntity tileEntity, double d0, double d1, double d2, float f,
            CallbackInfo ci) {
        if (tileEntity != null && "jp.ngt.rtm.rail.TileEntityLargeRailCore".equals(tileEntity.getClass().getName())) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.renderViewEntity != null) {
                double dx = tileEntity.xCoord + 0.5D - mc.renderViewEntity.posX;
                double dy = tileEntity.yCoord + 0.5D - mc.renderViewEntity.posY;
                double dz = tileEntity.zCoord + 0.5D - mc.renderViewEntity.posZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > MAX_RENDER_DIST_SQ) {
                    ci.cancel();
                }
            }
        }
    }
}
