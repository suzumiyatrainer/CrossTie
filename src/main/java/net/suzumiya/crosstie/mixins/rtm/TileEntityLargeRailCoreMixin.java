package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.EntityPositionHelper;
import net.suzumiya.crosstie.util.RailAabbResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.rail.TileEntityLargeRailCore", remap = false)
public abstract class TileEntityLargeRailCoreMixin extends TileEntity {

    private static final int CROSSTIE_FORCE_RENDER_CHUNKS = 3;

    private static final double CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS = CROSSTIE_FORCE_RENDER_CHUNKS * 16.0D;

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        int renderDistance = CrossTie.proxy.getClientRenderDistance();
        if (renderDistance > 0) {
            double blockDistance = (renderDistance + CROSSTIE_FORCE_RENDER_CHUNKS) * 16.0D;
            return blockDistance * blockDistance;
        }
        return CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS * CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS;
    }

    @Inject(method = "getRenderBoundingBox", at = @At("RETURN"), cancellable = true, remap = false)
    @SideOnly(Side.CLIENT)
    private void crosstie$fixAngelicaRailCulling(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (CrossTieConfig.fixAngelicaRailCulling) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderViewEntity == null) {
            return;
        }
        double[] viewerPos = new double[3];
        if (!EntityPositionHelper.tryGetPosition(mc.renderViewEntity, viewerPos)) {
            return;
        }

        AxisAlignedBB railAabb = RailAabbResolver.getEffectiveRailAabb(this, cir.getReturnValue());
        if (railAabb == null) {
            // Keep rail visible when bounds introspection fails for branch rail variants.
            cir.setReturnValue(INFINITE_EXTENT_AABB);
            return;
        }

        double forceRenderDistanceSq = CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS * CROSSTIE_FORCE_RENDER_DISTANCE_BLOCKS;
        if (RailAabbResolver.distanceSqToAabb(viewerPos[0], viewerPos[1], viewerPos[2], railAabb) <= forceRenderDistanceSq) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
        }
    }
}
