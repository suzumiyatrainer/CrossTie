package net.suzumiya.crosstie.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.suzumiya.crosstie.Tags;

public class CrossTiePacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        INSTANCE.registerMessage(MessageRemoveWireHandler.class, MessageRemoveWire.class, packetId++, Side.SERVER);
        
        INSTANCE.registerMessage(MessagePlayTrainSoundHandler.class, MessagePlayTrainSound.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(MessagePlayStationSoundHandler.class, MessagePlayStationSound.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(MessageStopSoundHandler.class, MessageStopSound.class, packetId++, Side.CLIENT);
    }
}
