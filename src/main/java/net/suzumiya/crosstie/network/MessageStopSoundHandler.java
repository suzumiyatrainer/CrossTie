package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class MessageStopSoundHandler implements IMessageHandler<MessageStopSound, IMessage> {
    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(MessageStopSound message, MessageContext ctx) {
        net.suzumiya.crosstie.client.sound.StationBroadcastSound sound = 
            net.suzumiya.crosstie.client.sound.StationBroadcastSound.ACTIVE_LOOPS.get(message.loopId);
        if (sound != null) {
            sound.stop();
        }
        return null;
    }
}
