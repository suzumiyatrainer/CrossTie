package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.Connection.ConnectionType;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;

public class MessageRemoveWireHandler implements IMessageHandler<MessageRemoveWire, IMessage> {

    @Override
    public IMessage onMessage(MessageRemoveWire message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        World world = player.worldObj;

        if (!CrossTieConfig.enableWireFastRemove) {
            return null;
        }

        // Anti-cheat / distance check
        // Check distance to the second point (which is where the player clicked to
        // delete)
        if (player.getDistanceSq(message.x2 + 0.5D, message.y2 + 0.5D, message.z2 + 0.5D) > 64.0D) {
            return null;
        }

        TileEntity te = world.getTileEntity(message.x1, message.y1, message.z1);
        if (te instanceof TileEntityElectricalWiring) {
            TileEntityElectricalWiring teew = (TileEntityElectricalWiring) te;

            Connection targetConnection = teew.getConnectionList().stream()
                    .filter(c -> c.x == message.x2 && c.y == message.y2 && c.z == message.z2).findFirst().orElse(null);

            if (targetConnection != null) {
                // Remove connection from both ends and sync
                teew.setConnectionTo(message.x2, message.y2, message.z2, ConnectionType.NONE, "");
            }
        }

        return null;
    }
}
