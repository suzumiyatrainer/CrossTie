package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityVehiclePart.class, remap = false)
public abstract class EntityFloorSeatCollisionMixin {

    /**
     * 座席・床パーツ（EntityFloor）の衝突ボックスをnullにし、車内歩行やカント傾斜時に
     * プレイヤーが座席やパーツのAABBに引っかかって車外へ弾き飛ばされる（押し出される）のを防止する。
     * （canBeCollidedWithはtrueのままなので右クリックでの着席は可能）
     */
    @Inject(method = {"getCollisionBox", "func_70114_g"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$nullifyCollisionBoxForSeats(Entity par1, CallbackInfoReturnable<AxisAlignedBB> cir) {
        if ((Object) this instanceof EntityFloor) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = {"getBoundingBox", "func_70046_E"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$nullifyBoundingBoxForSeats(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if ((Object) this instanceof EntityFloor) {
            cir.setReturnValue(null);
        }
    }
}
