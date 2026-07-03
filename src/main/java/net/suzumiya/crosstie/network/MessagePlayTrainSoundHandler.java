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
        if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) {
            System.out.println("[CrossTie-Debug] Client received packet. trainId=" + message.entityId + ", sound=" + message.soundName + ", type=" + message.type);
        }
        if (mc.theWorld != null) {
            Entity entity = mc.theWorld.getEntityByID(message.entityId);
            if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] Found entity: " + (entity != null ? entity.getClass().getName() : "null"));
            if (entity instanceof EntityTrainBase) {
                net.suzumiya.crosstie.client.sound.TrainLineSegmentSound sound = 
                    new net.suzumiya.crosstie.client.sound.TrainLineSegmentSound(
                        (EntityTrainBase) entity, message.length, message.maxRadius, message.soundName, message.type
                    );
                if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] Playing sound object: " + sound);
                mc.getSoundHandler().playSound(sound);
            }
        } else {
            if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] Client world is null.");
        }
        return null;
    }
}
