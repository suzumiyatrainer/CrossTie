package net.suzumiya.crosstie.client;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.suzumiya.crosstie.utils.TrainStandingHandler;

/**
 * クライアント専用の立ち乗り制御クラス。
 * Dedicated Server 環境で Minecraft / EntityClientPlayerMP のクラスロードによる
 * NoClassDefFoundError を防止するため、クライアント専用コードを本クラスに完全隔離する。
 */
@SideOnly(Side.CLIENT)
public class TrainStandingClientHandler {

    private static EntityTrainBase clientStandingTrain = null;

    public static EntityTrainBase getClientStandingTrain() {
        return clientStandingTrain;
    }

    public static void clearClientStandingTrain(EntityTrainBase train) {
        if (clientStandingTrain == train) {
            clientStandingTrain = null;
        }
    }

    public static void onTrainUpdateClient(EntityTrainBase train, List<Entity> nearby, float dYaw, double halfLength, double halfWidth, double speed) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer localPlayer = mc.thePlayer;
        if (localPlayer == null) {
            clientStandingTrain = null;
            return;
        }

        boolean containsLocal = nearby.contains(localPlayer);
        boolean alreadyRiding = TrainStandingHandler.isPlayerRiding(localPlayer.getUniqueID());
        boolean standing = containsLocal && TrainStandingHandler.isStandingOnTrain(train, halfLength, halfWidth, localPlayer, alreadyRiding, speed);

        if (standing) {
            // 視線旋回 (Yaw) の追従
            if (dYaw != 0.0f) {
                localPlayer.rotationYaw -= dYaw;
                localPlayer.prevRotationYaw -= dYaw;
            }

            // 3次元ローカル追従
            movePlayerWithTrainClient(localPlayer, train);
            clientStandingTrain = train;
            TrainStandingHandler.setPlayerRiding(localPlayer.getUniqueID(), true);
        } else {
            if (clientStandingTrain == train) {
                clientStandingTrain = null;
                TrainStandingHandler.setPlayerRiding(localPlayer.getUniqueID(), false);
            }
        }
    }

    /**
     * クライアント側（ローカルプレイヤー）の3次元ローカル追従
     * カント（Roll）や勾配（Pitch）による車体の立体的な傾き・変位に追従
     */
    private static void movePlayerWithTrainClient(EntityPlayer player, EntityTrainBase train) {
        Vec3 localVec = TrainStandingHandler.computeLocalPosPrev(train, player.posX, player.posY, player.posZ);
        Vec3 targetWorld = TrainStandingHandler.computeTransformedWorldPos(train, localVec.zCoord, localVec.xCoord, 0.0);

        double targetX = targetWorld.xCoord;
        double targetZ = targetWorld.zCoord;
        double moveX = targetX - player.posX;
        double moveZ = targetZ - player.posZ;

        double targetFloorY = TrainStandingHandler.computeFloorY(train, localVec.zCoord, localVec.xCoord);
        double yError = targetFloorY - player.boundingBox.minY;
        double yCorrection = 0.0;
        if (player.boundingBox.minY - targetFloorY < 0.6) {
            yCorrection = MathHelper.clamp_double(yError * 0.4, -0.6, 0.6);
            if (Math.abs(yError) > 0.6) {
                yCorrection = yError;
            }
        }

        boolean prevNoClip = player.noClip;
        player.noClip = true;
        player.moveEntity(moveX, yCorrection, moveZ);
        player.noClip = prevNoClip;

        player.motionY = 0.0;
        player.fallDistance = 0.0f;
        player.onGround = true;
    }
}
