package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.network.NetworkRegistry;
import jp.ngt.rtm.CommonProxy;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.network.PacketPlaySound;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CommonProxy.playSound において、一律 256.0F で送信されていた TargetPoint 半径を
 * サウンドの実際の到達範囲 (range) に合わせた最適半径に絞り込み、サーバー負荷と無用なパケット発信を防止します。
 */
@Mixin(value = CommonProxy.class, remap = false)
public abstract class CommonProxySoundRangeOptimizationMixin {

    @Inject(
        method = "playSound(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/ResourceLocation;FFF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlaySoundEntity(Entity entity, ResourceLocation sound, float vol, float pitch, float range, CallbackInfo ci) {
        if (sound != null && entity != null && entity.worldObj != null) {
            // 音が聞こえる到達距離（range）に合わせた最適な探索半径を計算 (最低16m、最大64m)
            double targetRange = Math.max(16.0D, Math.min((double) range * 1.5D, 64.0D));

            RTMCore.NETWORK_WRAPPER.sendToAllAround(
                new PacketPlaySound(entity, sound, vol, pitch, range),
                new NetworkRegistry.TargetPoint(entity.dimension, entity.posX, entity.posY, entity.posZ, targetRange)
            );
        }
        ci.cancel();
    }

    @Inject(
        method = "playSound(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/util/ResourceLocation;FFF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlaySoundTileEntity(TileEntity entity, ResourceLocation sound, float vol, float pitch, float range, CallbackInfo ci) {
        if (sound != null && entity != null && entity.getWorldObj() != null) {
            double targetRange = Math.max(16.0D, Math.min((double) range * 1.5D, 64.0D));

            RTMCore.NETWORK_WRAPPER.sendToAllAround(
                new PacketPlaySound(entity, sound, vol, pitch, range),
                new NetworkRegistry.TargetPoint(
                    entity.getWorldObj().provider.dimensionId,
                    (double) entity.xCoord + 0.5D,
                    (double) entity.yCoord + 0.5D,
                    (double) entity.zCoord + 0.5D,
                    targetRange
                )
            );
        }
        ci.cancel();
    }
}
