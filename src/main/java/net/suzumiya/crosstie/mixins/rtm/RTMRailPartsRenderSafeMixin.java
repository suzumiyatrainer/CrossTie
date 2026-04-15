package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.client.Minecraft;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reimplements renderRail with explicit cleanup so hi03 context cannot leak into
 * unrelated Angelica display-list compilation.
 */
@Mixin(targets = "jp.ngt.rtm.render.RailPartsRenderer", remap = false)
public abstract class RTMRailPartsRenderSafeMixin {

    @Shadow
    protected int currentRailIndex;

    @Shadow
    protected abstract void renderRailStatic(TileEntityLargeRailCore tileEntity, double x, double y, double z, float par8);

    @Shadow
    protected abstract void renderRailDynamic(TileEntityLargeRailCore tileEntity, double x, double y, double z, float par8);

    @Inject(method = "renderRail", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$renderRailSafely(TileEntityLargeRailCore tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        if (this.crosstie$shouldCullRail(tileEntity)) {
            ci.cancel();
            return;
        }

        try {
            this.currentRailIndex = index;
            if (this.crosstie$isHi03Rail(tileEntity)) {
                Hi03ExpressRailwayContext.enter();
            }

            this.renderRailStatic(tileEntity, x, y, z, par8);
            this.renderRailDynamic(tileEntity, x, y, z, par8);
        } catch (Exception e) {
            throw new RuntimeException("On init script", e);
        } finally {
            if (Hi03ExpressRailwayContext.isUsingLegacyDisplayList()) {
                try {
                    GL11.glEndList();
                } catch (RuntimeException ignored) {
                    // Best-effort cleanup if compile aborted after opening a native list.
                }
            }
            Hi03ExpressRailwayContext.reset();
        }

        ci.cancel();
    }

    private boolean crosstie$isHi03Rail(TileEntityLargeRailCore tileEntity) {
        try {
            RailProperty property = tileEntity.getProperty();
            return property != null && property.railModel != null
                    && property.railModel.contains("hi03ExpressRailway");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean crosstie$shouldCullRail(TileEntityLargeRailCore tileEntity) {
        if (!CrossTieConfig.enableRenderCulling) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null) {
            return false;
        }

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        double cullDist = renderChunks * 16.0;
        return tileEntity.getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
                mc.renderViewEntity.posZ) > cullDist * cullDist;
    }
}
