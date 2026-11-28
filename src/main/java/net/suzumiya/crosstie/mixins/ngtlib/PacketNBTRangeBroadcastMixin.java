package net.suzumiya.crosstie.mixins.ngtlib;

import cpw.mods.fml.common.network.NetworkRegistry;
import jp.ngt.ngtlib.NGTCore;
import jp.ngt.ngtlib.network.PacketNBT;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * Phase 4-3: {@link PacketNBT#sendToClient} の全プレイヤー配信（{@code sendToAll}）を
 * 追跡プレイヤー / チャンク監視プレイヤーへ限定する。
 *
 * <p>別ディメンションや遠距離プレイヤーへの不要な NBT 送信を排除し、マルチプレイ帯域を削減する。</p>
 */
@Mixin(value = PacketNBT.class, remap = false)
public abstract class PacketNBTRangeBroadcastMixin {

    @Inject(method = "sendToClient(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$sendEntityToTrackers(Entity entity, CallbackInfo ci) {
        if (entity == null) {
            ci.cancel();
            return;
        }
        if (entity.worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) entity.worldObj;
            PacketNBT packet = new PacketNBT(entity, true);
            Set<?> tracking = ws.getEntityTracker().getTrackingPlayers(entity);
            boolean sent = false;
            if (tracking != null && !tracking.isEmpty()) {
                for (Object obj : tracking) {
                    if (obj instanceof EntityPlayerMP) {
                        NGTCore.NETWORK_WRAPPER.sendTo(packet, (EntityPlayerMP) obj);
                        sent = true;
                    }
                }
            }
            if (entity instanceof EntityPlayerMP) {
                NGTCore.NETWORK_WRAPPER.sendTo(packet, (EntityPlayerMP) entity);
                sent = true;
            }
            if (!sent) {
                NGTCore.NETWORK_WRAPPER.sendToAllAround(
                        packet,
                        new NetworkRegistry.TargetPoint(
                                ws.provider.dimensionId,
                                entity.posX, entity.posY, entity.posZ,
                                256.0D));
            }
            ci.cancel();
        }
    }

    @Inject(method = "sendToClient(Lnet/minecraft/tileentity/TileEntity;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void crosstie$sendTileToChunkWatchers(TileEntity entity, CallbackInfo ci) {
        if (entity == null) {
            ci.cancel();
            return;
        }
        World world = entity.getWorldObj();
        if (world instanceof WorldServer) {
            WorldServer ws = (WorldServer) world;
            PacketNBT packet = new PacketNBT(entity, true);
            PlayerManager pm = ws.getPlayerManager();
            int chunkX = entity.xCoord >> 4;
            int chunkZ = entity.zCoord >> 4;
            List<?> players = ws.playerEntities;
            for (int i = 0, len = players.size(); i < len; ++i) {
                Object obj = players.get(i);
                if (obj instanceof EntityPlayerMP) {
                    EntityPlayerMP mp = (EntityPlayerMP) obj;
                    if (pm.isPlayerWatchingChunk(mp, chunkX, chunkZ)) {
                        NGTCore.NETWORK_WRAPPER.sendTo(packet, mp);
                    }
                }
            }
            ci.cancel();
        }
    }
}
