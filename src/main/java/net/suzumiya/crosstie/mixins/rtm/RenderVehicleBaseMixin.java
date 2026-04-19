package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.AngelicaRenderGuard;
import net.suzumiya.crosstie.util.AngelicaShaderFlagBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.rtm.entity.vehicle.RenderVehicleBase", remap = false)
public abstract class RenderVehicleBaseMixin {

    @Inject(method = "doRender", at = @At("HEAD"), remap = false)
    private void crosstie$applyTrainShaderFixFlags(Entity entity, double x, double y, double z, float yaw,
            float partialTicks, CallbackInfo ci) {
        if (CrossTieConfig.enableAngelicaFallbackGuard && (AngelicaRenderGuard.hasInvalidDouble(x)
                || AngelicaRenderGuard.hasInvalidDouble(y)
                || AngelicaRenderGuard.hasInvalidDouble(z))) {
            AngelicaRenderGuard.triggerFallback();
            return;
        }
        AngelicaShaderFlagBridge.applyFlags(true, false, AngelicaRenderGuard.isFallbackActive());
    }

    @Inject(method = "doRender", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantVehicles(Entity entity, double x, double y, double z, float yaw,
            float partialTicks, CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderDistChunks = mc.gameSettings.renderDistanceChunks;
        double cullDist = (renderDistChunks + 2) * 16.0;
        double distSq = entity.getDistanceSqToEntity(mc.renderViewEntity);

        if (distSq > cullDist * cullDist) {
            ci.cancel();
        }
    }

    @Inject(method = "doRender", at = @At("RETURN"), remap = false)
    private void crosstie$clearTrainShaderFixFlags(Entity entity, double x, double y, double z, float yaw,
            float partialTicks, CallbackInfo ci) {
        AngelicaShaderFlagBridge.applyFlags(false, false, AngelicaRenderGuard.isFallbackActive());
    }

    @Inject(method = "renderLightEffect(Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;Ljp/ngt/rtm/modelpack/modelset/ModelSetVehicleBaseClient;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullLightEffects(@Coerce Object vehicle, @Coerce Object modelset,
            CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        int effectChunks = Math.max(4, renderChunks - 4);
        double effectDist = effectChunks * 16.0;

        if (((Entity) vehicle).getDistanceSqToEntity(mc.renderViewEntity) > effectDist * effectDist) {
            ci.cancel();
        }
    }

    @Inject(method = "renderRollsign(Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;Ljp/ngt/rtm/modelpack/modelset/ModelSetVehicleBaseClient;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRollsigns(@Coerce Object vehicle, @Coerce Object modelset,
            CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        double signDist = (renderChunks + 1) * 16.0;

        if (((Entity) vehicle).getDistanceSqToEntity(mc.renderViewEntity) > signDist * signDist) {
            ci.cancel();
        }
    }
}
