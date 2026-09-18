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
     * 座席（seatType != 0）の場合のみ、他エンティティとの物理衝突（getBoundingBox）をなくす。
     * EntityFloor は getBoundingBox をオーバーライドしていないため、
     * 親クラスである EntityVehiclePart に Mixin して instanceof で判定する。
     */
    @Inject(method = {"getBoundingBox", "func_70046_E"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$nullifyBoundingBoxForSeats(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if ((Object) this instanceof EntityFloor) {
            if (((EntityFloor) (Object) this).getSeatType() != 0) {
                cir.setReturnValue(null);
            }
        }
    }

    /**
     * 座席（seatType != 0）の場合のみ、getCollisionBoxもnullにする。
     * 既に別の Mixin が EntityVehiclePart 全体に対して null を返している場合でも、
     * ここで座席用に明示的に処理しておく。
     */
    @Inject(method = {"getCollisionBox", "func_70114_g"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$nullifyCollisionBoxForSeats(Entity par1, CallbackInfoReturnable<AxisAlignedBB> cir) {
        if ((Object) this instanceof EntityFloor) {
            if (((EntityFloor) (Object) this).getSeatType() != 0) {
                cir.setReturnValue(null);
            }
        }
    }
}
