package net.suzumiya.crosstie.physics;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailMap;

/**
 * ワーカースレッド内で計算された台車の物理状態。
 */
public class BogiePhysicsResult {
    public final double posX, posY, posZ;
    public final float yaw, pitch, roll, movingYaw, cant;
    public final int prevPosIndex, split;
    public final TileEntityLargeRailCore currentRailObj;
    public final RailMap currentRailMap;
    public final float jointDelay;

    public BogiePhysicsResult(
            double posX, double posY, double posZ,
            float yaw, float pitch, float roll, float movingYaw, float cant,
            int prevPosIndex, int split,
            TileEntityLargeRailCore currentRailObj, RailMap currentRailMap,
            float jointDelay) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.movingYaw = movingYaw;
        this.cant = cant;
        this.prevPosIndex = prevPosIndex;
        this.split = split;
        this.currentRailObj = currentRailObj;
        this.currentRailMap = currentRailMap;
        this.jointDelay = jointDelay;
    }
}
