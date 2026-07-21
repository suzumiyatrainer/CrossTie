package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import jp.ngt.ngtlib.network.PacketNBT;
import jp.ngt.rtm.entity.vehicle.VehicleTrackerEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = VehicleTrackerEntry.class, remap = false)
public abstract class VehicleTrackerEntryMixin {

    @Unique
    private int crosstie$ticks = 0;

    /**
     * @author Antigravity
     * @reason 追跡開始時のNBT同期を、全サーバー一斉送信から対象プレイヤーへの個別送信にリダイレクト
     */
    @Redirect(method = "tryStartWachingThis(Lnet/minecraft/entity/player/EntityPlayerMP;)V",
              at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/network/PacketNBT;sendToClient(Lnet/minecraft/entity/Entity;)V"))
    private void redirectSendToClient(Entity entity, EntityPlayerMP player) {
        PacketNBT.sendTo(entity, player);
    }

    /**
     * @author Antigravity
     * @reason 車両位置同期パケットの送信処理をリダイレクト
     * 追跡プレイヤーには毎回送信、追跡外プレイヤーへは10ティック（0.5秒）に1回のみに制限
     */
    @Redirect(method = "sendLocationToAllClients(Ljava/util/List;)V",
              at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;sendToAll(Lcpw/mods/fml/common/network/simpleimpl/IMessage;)V"))
    private void redirectSendToAll(SimpleNetworkWrapper wrapper, IMessage message, List<?> trackingPlayers) {
        // 追跡プレイヤーには毎回送信
        if (trackingPlayers != null) {
            for (Object obj : trackingPlayers) {
                if (obj instanceof EntityPlayerMP) {
                    wrapper.sendTo(message, (EntityPlayerMP) obj);
                }
            }
        }

        // 追跡外プレイヤーへは10ティックに1回送信
        crosstie$ticks = (crosstie$ticks + 1) % 10;
        if (crosstie$ticks == 0) {
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (server != null) {
                @SuppressWarnings("unchecked")
                java.util.List<EntityPlayerMP> allPlayers = server.getConfigurationManager().playerEntityList;
                for (EntityPlayerMP player : allPlayers) {
                    if (trackingPlayers == null || !trackingPlayers.contains(player)) {
                        wrapper.sendTo(message, player);
                    }
                }
            }
        }
    }
}
