package net.suzumiya.crosstie.entity;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.suzumiya.crosstie.utils.TrainStandingHandler;

/** Invisible, train-following collision surface used by standing passengers. */
public class EntityStandingFloor extends Entity {
    private static final int OWNER_WATCHER_ID = 20;
    private static final int SEGMENT_INDEX_WATCHER_ID = 21;
    private static final int SEGMENT_START_WATCHER_ID = 22;
    private static final int SEGMENT_END_WATCHER_ID = 23;
    private static final double HALF_WIDTH = 1.375D;
    private static final double THICKNESS = 0.0625D;
    public static final double SEGMENT_LENGTH = 2.5D;

    public EntityStandingFloor(World world) {
        super(world);
        setSize(1.0F, (float) THICKNESS);
        preventEntitySpawning = true;
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(OWNER_WATCHER_ID, -1);
        dataWatcher.addObject(SEGMENT_INDEX_WATCHER_ID, -1);
        dataWatcher.addObject(SEGMENT_START_WATCHER_ID, 0.0F);
        dataWatcher.addObject(SEGMENT_END_WATCHER_ID, 0.0F);
    }

    public void setTrain(EntityTrainBase train, int segmentIndex, double localStart, double localEnd) {
        dataWatcher.updateObject(OWNER_WATCHER_ID, train.getEntityId());
        dataWatcher.updateObject(SEGMENT_INDEX_WATCHER_ID, segmentIndex);
        dataWatcher.updateObject(SEGMENT_START_WATCHER_ID, (float) localStart);
        dataWatcher.updateObject(SEGMENT_END_WATCHER_ID, (float) localEnd);
        updateSurface(train);
    }

    public int getTrainEntityId() {
        return dataWatcher.getWatchableObjectInt(OWNER_WATCHER_ID);
    }

    public int getSegmentIndex() {
        return dataWatcher.getWatchableObjectInt(SEGMENT_INDEX_WATCHER_ID);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        Entity owner = worldObj.getEntityByID(getTrainEntityId());
        if (!(owner instanceof EntityTrainBase) || owner.isDead) {
            setDead();
            return;
        }
        updateSurface((EntityTrainBase) owner);
    }

    private void updateSurface(EntityTrainBase train) {
        double localStart = dataWatcher.getWatchableObjectFloat(SEGMENT_START_WATCHER_ID);
        double localEnd = dataWatcher.getWatchableObjectFloat(SEGMENT_END_WATCHER_ID);
        Vec3[] corners = new Vec3[] {
                TrainStandingHandler.computeFloorWorldPos(train, localStart, -HALF_WIDTH),
                TrainStandingHandler.computeFloorWorldPos(train, localStart, HALF_WIDTH),
                TrainStandingHandler.computeFloorWorldPos(train, localEnd, -HALF_WIDTH),
                TrainStandingHandler.computeFloorWorldPos(train, localEnd, HALF_WIDTH)
        };
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            minX = Math.min(minX, corner.xCoord);
            minZ = Math.min(minZ, corner.zCoord);
            maxX = Math.max(maxX, corner.xCoord);
            maxZ = Math.max(maxZ, corner.zCoord);
        }

        // A vanilla entity collision box is axis-aligned. Use the transformed
        // surface center for its height and the transformed footprint for X/Z.
        double floorY = TrainStandingHandler.computeFloorY(train, 0.0D, 0.0D);
        posX = train.posX;
        posY = floorY - THICKNESS;
        posZ = train.posZ;
        boundingBox.setBounds(minX, floorY - THICKNESS, minZ, maxX, floorY, maxZ);
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }

    @Override
    public AxisAlignedBB getCollisionBox(Entity entity) {
        return entity instanceof EntityStandingFloor ? null : entity.getBoundingBox();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
    }
}
