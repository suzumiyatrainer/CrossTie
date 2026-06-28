package net.suzumiya.crosstie.api.sound;

import jp.ngt.rtm.entity.train.EntityTrainBase;

public class TrainSoundContext {

    public enum DoorSide {
        NONE, LEFT, RIGHT, BOTH;
        
        public static DoorSide fromByte(byte b) {
            if ((b & 3) == 3) return BOTH;
            if ((b & 1) == 1) return LEFT;
            if ((b & 2) == 2) return RIGHT;
            return NONE;
        }
    }

    private final EntityTrainBase train;
    private int notch;
    private byte doorState;
    private DoorSide lastOpenSide = DoorSide.NONE;
    private float speed;

    public TrainSoundContext(EntityTrainBase train) {
        this.train = train;
    }

    public void setNotch(int notch) {
        this.notch = notch;
    }

    public void setDoorState(byte doorState) {
        this.doorState = doorState;
        DoorSide currentOpen = DoorSide.fromByte(doorState);
        if (currentOpen != DoorSide.NONE) {
            this.lastOpenSide = currentOpen;
        }
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public int getNotch() {
        return notch;
    }

    public byte getDoorState() {
        return doorState;
    }
    
    public DoorSide getLastOpenSide() {
        return lastOpenSide;
    }

    public float getSpeed() {
        return speed;
    }

    public EntityTrainBase getTrain() {
        return train;
    }
}
