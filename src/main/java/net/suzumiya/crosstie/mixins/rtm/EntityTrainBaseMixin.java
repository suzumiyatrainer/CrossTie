package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 列車エンティティ共通の最適化
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseMixin {

    /**
     * 描画距離外のエンティティの更新をスキップ
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
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
}
