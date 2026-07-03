package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;

public class MessagePlayTrainSound implements IMessage {
    public int entityId;
    public float length;
    public float maxRadius;
    public String soundName;
    public byte type; // 0 = InCar, 1 = Exterior

    public MessagePlayTrainSound() {}

    public MessagePlayTrainSound(int entityId, float length, float maxRadius, String soundName, byte type) {
        this.entityId = entityId;
        this.length = length;
        this.maxRadius = maxRadius;
        this.soundName = soundName;
        this.type = type;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.length = buf.readFloat();
        this.maxRadius = buf.readFloat();
        this.soundName = ByteBufUtils.readUTF8String(buf);
        this.type = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(length);
        buf.writeFloat(maxRadius);
        ByteBufUtils.writeUTF8String(buf, soundName);
        buf.writeByte(type);
    }
}
