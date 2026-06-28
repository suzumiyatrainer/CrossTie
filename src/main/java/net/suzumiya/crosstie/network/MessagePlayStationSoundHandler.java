package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

public class MessagePlayStationSoundHandler implements IMessageHandler<MessagePlayStationSound, IMessage> {
    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(MessagePlayStationSound message, MessageContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            net.suzumiya.crosstie.client.sound.StationBroadcastSound sound = 
                new net.suzumiya.crosstie.client.sound.StationBroadcastSound(
                    message.coords, message.maxRadius, message.soundName, message.loopId, message.isLoop
                );
            mc.getSoundHandler().playSound(sound);
        }
        return null;
    }
}
