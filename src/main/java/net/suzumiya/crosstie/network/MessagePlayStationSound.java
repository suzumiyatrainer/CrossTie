package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class MessagePlayStationSound implements IMessage {
    public double[] coords; // [x1,y1,z1, x2,y2,z2, ...]
    public float maxRadius;
    public String soundName;
    public String loopId;
    public boolean isLoop;

    public MessagePlayStationSound() {}

    public MessagePlayStationSound(double[][] coordList, float maxRadius, String soundName, String loopId, boolean isLoop) {
        this.coords = new double[coordList.length * 3];
        int i = 0;
        for (double[] c : coordList) {
            this.coords[i++] = c[0];
            this.coords[i++] = c[1];
            this.coords[i++] = c[2];
        }
        this.maxRadius = maxRadius;
        this.soundName = soundName;
        this.loopId = loopId != null ? loopId : "";
        this.isLoop = isLoop;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        this.coords = new double[len * 3];
        for (int i = 0; i < len * 3; i++) {
            this.coords[i] = buf.readDouble();
        }
        this.maxRadius = buf.readFloat();
        this.soundName = ByteBufUtils.readUTF8String(buf);
        this.loopId = ByteBufUtils.readUTF8String(buf);
        this.isLoop = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(coords.length / 3);
        for (double c : coords) {
            buf.writeDouble(c);
        }
        buf.writeFloat(maxRadius);
        ByteBufUtils.writeUTF8String(buf, soundName);
        ByteBufUtils.writeUTF8String(buf, loopId);
        buf.writeBoolean(isLoop);
    }
}
