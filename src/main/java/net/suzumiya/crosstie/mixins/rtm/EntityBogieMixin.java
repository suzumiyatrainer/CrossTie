package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityBogie の更新を軽量化する mixin。
 *
 * サーバー側のアニメーション処理を止め、クライアント側も描画距離外では更新しない。
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityBogie", remap = false)
public abstract class EntityBogieMixin {

    /**
     * サーバー側では車輪アニメーションを実行しない。
     */
    @Inject(method = "updateWheelRotation", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$skipServerAnimation(CallbackInfo ci) {
        Entity bogie = (Entity) (Object) this;
        if (!bogie.worldObj.isRemote) {
            ci.cancel();
        }
    }

    /**
     * 描画距離外では bogie の更新を間引く。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        Entity bogie = (Entity) (Object) this;
        if (bogie.worldObj.isRemote) {
            // クライアント側の描画距離に応じて更新を抑制する
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            double cullLimit = (renderChunks + 2) * 16.0;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && bogie.getDistanceSqToEntity(player) > limitSq) {
                ci.cancel();
            }
        }
    }
}
