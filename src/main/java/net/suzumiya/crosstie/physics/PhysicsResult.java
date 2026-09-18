package net.suzumiya.crosstie.physics;

import jp.ngt.rtm.entity.train.EntityTrainBase;


/**
 * ワーカースレッド内で計算された1編成（または1車両）の物理状態。
 */
public class PhysicsResult {
    public final EntityTrainBase train;
    public final double posX, posY, posZ;
    public final float yaw, pitch, roll;
    public final BogiePhysicsResult frontBogie;
    public final BogiePhysicsResult backBogie;

    public PhysicsResult(EntityTrainBase train,
                         double posX, double posY, double posZ,
                         float yaw, float pitch, float roll,
                         BogiePhysicsResult frontBogie, BogiePhysicsResult backBogie) {
        this.train = train;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.frontBogie = frontBogie;
        this.backBogie = backBogie;
    }
}
