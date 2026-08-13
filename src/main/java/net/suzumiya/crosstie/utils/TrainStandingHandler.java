package net.suzumiya.crosstie.utils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class TrainStandingHandler {
    private static final double SURFACE_MARGIN = 0.2;
    private static final double RIDE_MARGIN = 1.0;
    private static final double SIDE_MARGIN = 0.15;
    private static final double Y_CORRECTION_FACTOR = 0.3;
    private static final double MAX_Y_CORRECTION = 0.5;
    private static final Set<UUID> ridingPlayers = Collections.newSetFromMap(new WeakHashMap<>());
    private static EntityTrainBase clientStandingTrain = null;

    public static EntityTrainBase getClientStandingTrain() {
        return clientStandingTrain;
    }

    @SuppressWarnings("unchecked")
    public static void updateStandingEntities(EntityTrainBase train) {
        double dx = train.posX - train.prevPosX;
        double dy = train.posY - train.prevPosY;
        double dz = train.posZ - train.prevPosZ;
        float dYaw = MathHelper.wrapAngleTo180_float((float) (train.rotationYaw - train.prevRotationYaw));
        double halfLength = TrainStandingHandler.getHalfLength(train);
        double halfWidth = 1.525;
        double searchExtent = halfLength + halfWidth + 1.0;
        AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox((double) (train.posX - searchExtent),
                (double) (train.posY - 3.0), (double) (train.posZ - searchExtent), (double) (train.posX + searchExtent),
                (double) (train.posY + 1.1875 + 4.0), (double) (train.posZ + searchExtent));
        List<Entity> nearby = train.worldObj.getEntitiesWithinAABBExcludingEntity((Entity) train, searchBox);
        boolean isRemote = train.worldObj.isRemote;
        for (Entity entity : nearby) {
            if (!(entity instanceof EntityPlayer) || entity.ridingEntity != null)
                continue;
            EntityPlayer player = (EntityPlayer) entity;
            boolean isLocalPlayer = false;
            if (isRemote && !(isLocalPlayer = player == Minecraft.getMinecraft().thePlayer))
                continue;
            UUID uuid = player.getUniqueID();
            boolean alreadyRiding = ridingPlayers.contains(uuid);
            boolean standing = TrainStandingHandler.isStandingOnTrain(train, halfLength, halfWidth, (Entity) player,
                    alreadyRiding);
            if (standing) {
                if (dYaw != 0.0f) {
                    player.rotationYaw -= dYaw;
                    if (isRemote && isLocalPlayer) {
                        player.prevRotationYaw -= dYaw;
                    }
                }
                TrainStandingHandler.movePlayerWithTrain(player, dx, dy, dz, dYaw, train);
                ridingPlayers.add(uuid);
                if (!isRemote || !isLocalPlayer)
                    continue;
                clientStandingTrain = train;
                continue;
            }
            if (!alreadyRiding)
                continue;
            ridingPlayers.remove(uuid);
            if (!isRemote || !isLocalPlayer || clientStandingTrain != train)
                continue;
            clientStandingTrain = null;
        }
    }

    private static void movePlayerWithTrain(EntityPlayer player, double dx, double dy, double dz, float dYaw,
            EntityTrainBase train) {
        double addZ;
        double addX;
        double relX;
        if (dYaw != 0.0f) {
            relX = player.posX - train.prevPosX;
            double relZ = player.posZ - train.prevPosZ;
            double rad = Math.toRadians(dYaw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double rotX = relX * cos + relZ * sin;
            double rotZ = relZ * cos - relX * sin;
            addX = train.posX + rotX - player.posX;
            addZ = train.posZ + rotZ - player.posZ;
        } else {
            addX = dx;
            addZ = dz;
        }
        relX = player.posX - train.posX;
        double relY = player.posY - (train.posY + train.getMountedYOffset());
        double relZ = player.posZ - train.posZ;
        Vec3 localVec = Vec3.createVectorHelper((double) relX, (double) relY, (double) relZ);
        localVec.rotateAroundY((float) Math.toRadians(-train.rotationYaw));
        localVec.rotateAroundX((float) Math.toRadians(-train.rotationPitch));
        localVec.rotateAroundZ((float) Math.toRadians(train.rotationRoll));
        double targetFloorY = TrainStandingHandler.computeFloorY(train, localVec.zCoord, localVec.xCoord);
        double yError = targetFloorY - player.boundingBox.minY;
        double yCorrection = 0.0;
        if (player.boundingBox.minY - targetFloorY < 0.5) {
            yCorrection = MathHelper.clamp_double((double) (yError * 0.3), (double) -0.5, (double) 0.5);
            if (Math.abs(yError) > 0.5) {
                yCorrection = yError;
            }
        }
        boolean prevNoClip = player.noClip;
        player.noClip = true;
        player.moveEntity(addX, dy + yCorrection, addZ);
        player.noClip = prevNoClip;
        player.motionY = 0.0;
        player.fallDistance = 0.0f;
        player.onGround = true;
    }

    private static double getHalfLength(EntityTrainBase train) {
        TrainConfig cfg = (TrainConfig) train.getModelSet().getConfig();
        if (cfg == null) {
            return 1.375;
        }
        return cfg.trainDistance;
    }

    private static boolean isStandingOnTrain(EntityTrainBase train, double halfLength, double halfWidth, Entity entity,
            boolean alreadyRiding) {
        boolean horizontalOverlap;
        double relX = entity.posX - train.posX;
        double relY = entity.posY - (train.posY + train.getMountedYOffset());
        double relZ = entity.posZ - train.posZ;
        Vec3 localVec = Vec3.createVectorHelper((double) relX, (double) relY, (double) relZ);
        localVec.rotateAroundY((float) Math.toRadians(-train.rotationYaw));
        localVec.rotateAroundX((float) Math.toRadians(-train.rotationPitch));
        localVec.rotateAroundZ((float) Math.toRadians(train.rotationRoll));
        double localLength = localVec.zCoord;
        double localWidth = localVec.xCoord;
        double widthMargin = alreadyRiding ? halfWidth + 0.6 : halfWidth;
        double lengthMargin = alreadyRiding ? halfLength + 0.5 : halfLength;
        boolean bl = horizontalOverlap = Math.abs(localWidth) <= widthMargin && Math.abs(localLength) <= lengthMargin;
        if (!horizontalOverlap) {
            return false;
        }
        double floorY = TrainStandingHandler.computeFloorY(train, localLength, localWidth);
        double feetY = entity.boundingBox.minY;
        double minVert = -2.5;
        double maxVert = alreadyRiding ? 2.0 : 1.8;
        double vertDiff = feetY - floorY;
        return vertDiff >= minVert && vertDiff <= maxVert;
    }

    public static Vec3 computeFloorWorldPos(EntityTrainBase train, double localL, double localW) {
        Vec3 vec = Vec3.createVectorHelper((double) localW, (double) 0.0, (double) localL);
        vec.rotateAroundZ((float) Math.toRadians(-train.rotationRoll));
        vec.rotateAroundX((float) Math.toRadians(train.rotationPitch));
        vec.rotateAroundY((float) Math.toRadians(train.rotationYaw));
        double worldY = train.posY + vec.yCoord + train.getMountedYOffset() - 0.145;
        return Vec3.createVectorHelper((double) (train.posX + vec.xCoord), (double) worldY,
                (double) (train.posZ + vec.zCoord));
    }

    public static double computeFloorY(EntityTrainBase train, double localL, double localW) {
        return TrainStandingHandler.computeFloorWorldPos((EntityTrainBase) train, (double) localL,
                (double) localW).yCoord;
    }
}
