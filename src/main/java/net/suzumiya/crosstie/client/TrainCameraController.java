package net.suzumiya.crosstie.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.entity.player.EntityPlayer;
import net.suzumiya.crosstie.utils.TrainStandingHandler;

@SideOnly(value=Side.CLIENT)
public final class TrainCameraController {
    private TrainCameraController() {
    }

    public static EntityVehicleBase<?> getMountedOrStandingVehicle(EntityPlayer player) {
        EntityFloor floor;
        if (player == null) {
            return null;
        }
        if (player.ridingEntity instanceof EntityFloor && (floor = (EntityFloor)player.ridingEntity).getVehicle() != null) {
            return floor.getVehicle();
        }
        if (player.ridingEntity instanceof EntityVehicleBase) {
            return (EntityVehicleBase<?>) player.ridingEntity;
        }
        EntityTrainBase standingTrain = TrainStandingHandler.getClientStandingTrain();
        if (standingTrain != null && !standingTrain.isDead) {
            return standingTrain;
        }
        return null;
    }

    public static float getPivotHeight(EntityPlayer player) {
        if (player == null) {
            return 1.62F;
        }
        // 着席時は座面からの頭の高さ(約1.1m~1.2m)、立っている時は足元からの頭の高さ(約1.62m)
        if (player.ridingEntity != null) {
            return 1.2F;
        }
        return 1.62F;
    }

    public static float getRollMultiplier(EntityPlayer player) {
        if (player == null) {
            return 1.0F;
        }
        // 運転席に座っている場合: カント追従を半減 (0.5F) にして、運転台や車体の傾きを実感できるようにする
        if (player.ridingEntity instanceof EntityVehicleBase) {
            return 0.5F;
        }
        return 1.0F;
    }

    public static void updateStandingYawFollow(float partialTicks) {
    }
}
