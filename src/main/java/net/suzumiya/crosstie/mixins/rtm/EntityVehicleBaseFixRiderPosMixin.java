package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityVehicleBase.class, remap = false)
public abstract class EntityVehicleBaseFixRiderPosMixin {

    /**
     * 座席から降りた際の位置補正処理（fixRiderPos）において、
     * 車両部品（座席）の getBoundingBox() が null の場合に NullPointerException が発生するのを防ぎつつ、
     * 降りた際にプレイヤーが不自然に1ブロック上などに浮いてしまう既存バグを修正する。
     * 座席の座標（実際の床の高さ付近）に直接プレイヤーを配置することで、
     * 下限（床面）に正確に降ろす。
     */
    @Inject(method = "fixRiderPos", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$guardFixRiderPos(EntityLivingBase entity, Entity vehicle, CallbackInfo ci) {
        if (vehicle instanceof EntityVehiclePart) {
            // 元のブロック検索処理を完全にスキップし、車両パーツ（座席）の座標に直接降ろす。
            // わずかにYを足すことで床エンティティの上に確実の乗せる（埋まり防止）。
            entity.setPositionAndUpdate(vehicle.posX, vehicle.posY + 0.05D, vehicle.posZ);
            ci.cancel();
        }
    }
}
