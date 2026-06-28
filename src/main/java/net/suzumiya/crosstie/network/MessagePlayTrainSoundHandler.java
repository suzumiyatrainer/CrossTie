package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import jp.ngt.rtm.entity.train.EntityTrainBase;

public class MessagePlayTrainSoundHandler implements IMessageHandler<MessagePlayTrainSound, IMessage> {
    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(MessagePlayTrainSound message, MessageContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            Entity entity = mc.theWorld.getEntityByID(message.entityId);
            if (entity instanceof EntityTrainBase) {
                net.suzumiya.crosstie.client.sound.TrainLineSegmentSound sound = 
                    new net.suzumiya.crosstie.client.sound.TrainLineSegmentSound(
                        (EntityTrainBase) entity, message.length, message.soundName, message.type
                    );
                mc.getSoundHandler().playSound(sound);
            }
        }
        return null;
    }
}
