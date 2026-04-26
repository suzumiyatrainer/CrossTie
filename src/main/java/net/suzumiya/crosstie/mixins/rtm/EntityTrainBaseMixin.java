package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.TrainSpatialTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityTrainBase の更新を描画距離で間引く。
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseMixin {

    /**
     * 描画距離の外にある列車は更新を止める。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.worldObj == null) {
            return;
        }
        if (!entity.worldObj.isRemote && CrossTieConfig.enableTrainSpatialTracker && entity.isDead) {
            TrainSpatialTracker.removeTrain(entity);
        }

        if (entity.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            double cullLimit = (renderChunks + 2) * 16.0;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && entity.getDistanceSqToEntity(player) > limitSq) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void crosstie$updateTrainSpatialIndex(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!CrossTieConfig.enableTrainSpatialTracker || entity.worldObj == null || entity.worldObj.isRemote) {
            return;
        }
        TrainSpatialTracker.updateTrain(entity);
    }
}
