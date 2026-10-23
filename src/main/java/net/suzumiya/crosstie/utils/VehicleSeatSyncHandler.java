package net.suzumiya.crosstie.utils;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.List;

public final class VehicleSeatSyncHandler {

    private VehicleSeatSyncHandler() {
    }

    /**
     * 車両（電車・気動車等）の移動完了直後に呼び出し、
     * 車両内の全座席および搭乗ライダーの位置・前tick位置を
     * 車両の時間軸（prevPos -> pos）と100%完全同期させる。
     */
    @SuppressWarnings("rawtypes")
    public static void syncVehicleFloorsAndRiders(EntityVehicleBase vehicle, List floors) {
        if (vehicle == null || vehicle.isDead) {
            return;
        }

        // 1. 車両内の全座席（EntityFloor）の同期
        if (floors != null) {
            for (int i = 0; i < floors.size(); i++) {
                Object obj = floors.get(i);
                if (obj instanceof EntityFloor) {
                    EntityFloor floor = (EntityFloor) obj;
                    if (!floor.isDead) {
                        syncFloorAndRider(floor, vehicle);
                    }
                }
            }
        }

        // 2. 運転席（車両本体に直接乗っている場合）の同期
        if (vehicle.riddenByEntity != null) {
            syncDirectRider(vehicle);
        }
    }

    /**
     * 座席（EntityFloor）とそのライダーの位置・前tick位置を、
     * 親車両の prevPos/pos および prevRotation/rotation から直接厳密に計算して同期する。
     */
    @SuppressWarnings("rawtypes")
    public static void syncFloorAndRider(EntityFloor floor, EntityVehicleBase vehicle) {
        if (floor == null || vehicle == null || floor.isDead) {
            return;
        }

        jp.ngt.ngtlib.math.Vec3 partVec = floor.getPartVec();
        if (partVec == null) {
            return;
        }
        double vx = partVec.getX();
        double vy = partVec.getY();
        double vz = partVec.getZ();

        // 今tickの位置（電車の今tick位置・姿勢から計算）
        Vec3 vCur = Vec3.createVectorHelper(vx, vy, vz);
        vCur.rotateAroundZ((float) Math.toRadians(-vehicle.rotationRoll));
        vCur.rotateAroundX((float) Math.toRadians(vehicle.rotationPitch));
        vCur.rotateAroundY((float) Math.toRadians(vehicle.rotationYaw));

        double curX = vehicle.posX + vCur.xCoord;
        double curY = vehicle.posY + vCur.yCoord;
        double curZ = vehicle.posZ + vCur.zCoord;

        // 前tickの位置（電車の前tick位置・姿勢から計算）
        Vec3 vPrev = Vec3.createVectorHelper(vx, vy, vz);
        vPrev.rotateAroundZ((float) Math.toRadians(-vehicle.prevRotationRoll));
        vPrev.rotateAroundX((float) Math.toRadians(vehicle.prevRotationPitch));
        vPrev.rotateAroundY((float) Math.toRadians(vehicle.prevRotationYaw));

        double prevX = vehicle.prevPosX + vPrev.xCoord;
        double prevY = vehicle.prevPosY + vPrev.yCoord;
        double prevZ = vehicle.prevPosZ + vPrev.zCoord;

        // 座席の位置・前tick位置を設定
        floor.prevPosX = prevX;
        floor.prevPosY = prevY;
        floor.prevPosZ = prevZ;
        floor.prevRotationYaw = vehicle.prevRotationYaw;
        floor.prevRotationPitch = vehicle.prevRotationPitch;

        floor.setPosition(curX, curY, curZ);
        floor.rotationYaw = vehicle.rotationYaw % 360.0F;
        floor.rotationPitch = vehicle.rotationPitch % 360.0F;
        floor.needsUpdatePos = false;

        // 座席に乗っているライダー（プレイヤー等）の同期
        if (floor.riddenByEntity != null) {
            Entity rider = floor.riddenByEntity;
            double mountedYOffset = floor.getMountedYOffset() + rider.getYOffset();

            rider.prevPosX = prevX;
            rider.prevPosY = prevY + mountedYOffset;
            rider.prevPosZ = prevZ;

            rider.setPosition(curX, curY + mountedYOffset, curZ);

            // 旋回（Yaw）追従
            float dYaw = MathHelper.wrapAngleTo180_float(vehicle.rotationYaw - vehicle.prevRotationYaw);
            if (dYaw != 0.0F) {
                rider.rotationYaw -= dYaw;
                rider.prevRotationYaw -= dYaw;
            }
        }
    }

    /**
     * 運転席（EntityTrainBase に直接乗っている場合）の同期
     */
    @SuppressWarnings("rawtypes")
    public static void syncDirectRider(EntityVehicleBase vehicle) {
        Entity rider = vehicle.riddenByEntity;
        if (rider == null || !(vehicle instanceof EntityTrainBase)) {
            return;
        }

        EntityTrainBase train = (EntityTrainBase) vehicle;
        try {
            float[][] pos = train.getModelSet().getConfig().getPlayerPos();
            int dir = train.getDataWatcher().getWatchableObjectByte(26); // DW_CabDir = 26
            if (dir < 0 || dir >= pos.length) {
                dir = 0;
            }

            double vx = pos[dir][0];
            double vy = pos[dir][1];
            double vz = pos[dir][2];

            Vec3 vCur = Vec3.createVectorHelper(vx, vy, vz);
            vCur.rotateAroundZ(NGTMath.toRadians(-train.rotationRoll));
            vCur.rotateAroundX(NGTMath.toRadians(train.rotationPitch));
            vCur.rotateAroundY(NGTMath.toRadians(train.rotationYaw));

            double curX = train.posX + vCur.xCoord;
            double curY = train.posY + vCur.yCoord;
            double curZ = train.posZ + vCur.zCoord;

            Vec3 vPrev = Vec3.createVectorHelper(vx, vy, vz);
            vPrev.rotateAroundZ(NGTMath.toRadians(-train.prevRotationRoll));
            vPrev.rotateAroundX(NGTMath.toRadians(train.prevRotationPitch));
            vPrev.rotateAroundY(NGTMath.toRadians(train.prevRotationYaw));

            double prevX = train.prevPosX + vPrev.xCoord;
            double prevY = train.prevPosY + vPrev.yCoord;
            double prevZ = train.prevPosZ + vPrev.zCoord;

            double mountedYOffset = train.getMountedYOffset() + rider.getYOffset();

            rider.prevPosX = prevX;
            rider.prevPosY = prevY + mountedYOffset;
            rider.prevPosZ = prevZ;

            rider.setPosition(curX, curY + mountedYOffset, curZ);

            float dYaw = MathHelper.wrapAngleTo180_float(train.rotationYaw - train.prevRotationYaw);
            if (dYaw != 0.0F) {
                rider.rotationYaw -= dYaw;
                rider.prevRotationYaw -= dYaw;
            }
        } catch (Throwable t) {
            train.updateRiderPosition();
        }
    }
}
