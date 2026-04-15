package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MovingMachine の更新頻度を抑える。
 *
 * 移動機構は挙動に影響しやすいため、サーバー側は最小限の制御に留める。
 */
@Mixin(targets = "jp.ngt.rtm.block.tileentity.TileEntityMovingMachine", remap = false)
public abstract class TileEntityMovingMachineMixin extends TileEntity {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        if (!CrossTieConfig.enableTileEntityUpdates || this.worldObj == null) {
            return;
        }

        if (this.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            double cullLimit = (renderChunks + 1) * 16.0D;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && this.getDistanceFrom(player.posX, player.posY, player.posZ) > limitSq) {
                ci.cancel();
            }
        } else {
            // サーバー側は過度な間引きによる事故を避けるため、限定的な抑制だけにする
            if ((this.worldObj.getTotalWorldTime() + this.hashCode()) % 5 == 0) {
                boolean isPlayerNear = false;
                double limitSq = 128.0 * 128.0;

                for (Object obj : this.worldObj.playerEntities) {
                    if (obj instanceof net.minecraft.entity.Entity) {
                        net.minecraft.entity.Entity p = (net.minecraft.entity.Entity) obj;
                        if (this.getDistanceFrom(p.posX, p.posY, p.posZ) < limitSq) {
                            isPlayerNear = true;
                            break;
                        }
                    }
                }

                if (!isPlayerNear) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "getMaxRenderDistanceSquared", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$limitMaxRenderDistance(CallbackInfoReturnable<Double> cir) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks <= 0) {
            return;
        }

        double renderDistance = (renderChunks + 1) * 16.0D;
        cir.setReturnValue(renderDistance * renderDistance);
    }
}
