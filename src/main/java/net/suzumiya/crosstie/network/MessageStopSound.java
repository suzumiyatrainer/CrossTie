package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class MessageStopSound implements IMessage {
    public String loopId;

    public MessageStopSound() {}

    public MessageStopSound(String loopId) {
        this.loopId = loopId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.loopId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, loopId);
    }
}
