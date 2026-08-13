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
        EntityTrainBase standingTrain = TrainStandingHandler.getClientStandingTrain();
        if (standingTrain != null && !standingTrain.isDead) {
            return standingTrain;
        }
        return null;
    }

    public static void updateStandingYawFollow(float partialTicks) {
    }
}
