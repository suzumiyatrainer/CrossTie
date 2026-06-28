package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class MessageRemoveWire implements IMessage {
    public int x1, y1, z1;
    public int x2, y2, z2;

    public MessageRemoveWire() {
    }

    public MessageRemoveWire(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x1 = buf.readInt();
        this.y1 = buf.readInt();
        this.z1 = buf.readInt();
        this.x2 = buf.readInt();
        this.y2 = buf.readInt();
        this.z2 = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x1);
        buf.writeInt(this.y1);
        buf.writeInt(this.z1);
        buf.writeInt(this.x2);
        buf.writeInt(this.y2);
        buf.writeInt(this.z2);
    }
}
