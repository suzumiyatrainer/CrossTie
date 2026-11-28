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

        // 車両が完全静止かつ非搭乗かつ更新不要なら回転計算をスキップ
        if (floor.riddenByEntity == null
                && !floor.needsUpdatePos
                && vehicle.getSpeed() == 0.0F
                && vehicle.posX == vehicle.prevPosX
                && vehicle.posY == vehicle.prevPosY
                && vehicle.posZ == vehicle.prevPosZ
                && vehicle.rotationYaw == vehicle.prevRotationYaw
                && vehicle.rotationPitch == vehicle.prevRotationPitch
                && vehicle.rotationRoll == vehicle.prevRotationRoll) {
            floor.prevPosX = floor.posX;
            floor.prevPosY = floor.posY;
            floor.prevPosZ = floor.posZ;
            return;
        }

        jp.ngt.ngtlib.math.Vec3 partVec = floor.getPartVec();
        if (partVec == null) {
            return;
        }
        double vx = partVec.getX();
        double vy = partVec.getY();
        double vz = partVec.getZ();

        // 今tickの位置（Vec3 生成ゼロのプリミティブ Z->X->Y 回転）
        float rz = (float) Math.toRadians(-vehicle.rotationRoll);
        float cosZ = MathHelper.cos(rz);
        float sinZ = MathHelper.sin(rz);
        double x1 = vx * cosZ + vy * sinZ;
        double y1 = vy * cosZ - vx * sinZ;
        double z1 = vz;

        float rx = (float) Math.toRadians(vehicle.rotationPitch);
        float cosX = MathHelper.cos(rx);
        float sinX = MathHelper.sin(rx);
        double y2 = y1 * cosX + z1 * sinX;
        double z2 = z1 * cosX - y1 * sinX;

        float ry = (float) Math.toRadians(vehicle.rotationYaw);
        float cosY = MathHelper.cos(ry);
        float sinY = MathHelper.sin(ry);
        double curX = vehicle.posX + (x1 * cosY + z2 * sinY);
        double curY = vehicle.posY + y2;
        double curZ = vehicle.posZ + (z2 * cosY - x1 * sinY);

        // 前tickの位置（車両が移動していなければ今tickの結果を再利用）
        double prevX;
        double prevY;
        double prevZ;
        if (vehicle.posX == vehicle.prevPosX
                && vehicle.posY == vehicle.prevPosY
                && vehicle.posZ == vehicle.prevPosZ
                && vehicle.rotationYaw == vehicle.prevRotationYaw
                && vehicle.rotationPitch == vehicle.prevRotationPitch
                && vehicle.rotationRoll == vehicle.prevRotationRoll) {
            prevX = curX;
            prevY = curY;
            prevZ = curZ;
        } else {
            float prz = (float) Math.toRadians(-vehicle.prevRotationRoll);
            float pCosZ = MathHelper.cos(prz);
            float pSinZ = MathHelper.sin(prz);
            double px1 = vx * pCosZ + vy * pSinZ;
            double py1 = vy * pCosZ - vx * pSinZ;
            double pz1 = vz;

            float prx = (float) Math.toRadians(vehicle.prevRotationPitch);
            float pCosX = MathHelper.cos(prx);
            float pSinX = MathHelper.sin(prx);
            double py2 = py1 * pCosX + pz1 * pSinX;
            double pz2 = pz1 * pCosX - py1 * pSinX;

            float pry = (float) Math.toRadians(vehicle.prevRotationYaw);
            float pCosY = MathHelper.cos(pry);
            float pSinY = MathHelper.sin(pry);
            prevX = vehicle.prevPosX + (px1 * pCosY + pz2 * pSinY);
            prevY = vehicle.prevPosY + py2;
            prevZ = vehicle.prevPosZ + (pz2 * pCosY - px1 * pSinY);
        }

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
