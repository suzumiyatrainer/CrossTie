package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntityMachineBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityMachineBase.class, remap = false)
public abstract class TileEntityMachineBaseMixin extends TileEntity {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        if (!CrossTieConfig.enableTileEntityUpdates || this.worldObj == null || !this.worldObj.isRemote) {
            return;
        }
        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks <= 0) {
            return;
        }

            // TileEntityは座標が固定なので、キャッシュ可能だが、プレイヤー移動により変わる
            // RenderDistance + 2 チャンク
        double cullLimit = (renderChunks + 1) * 16.0D;
        double limitSq = cullLimit * cullLimit;

        net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
        if (player != null
                && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) > limitSq) {
            ci.cancel();
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

    @Inject(method = "getRenderBoundingBox", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$keepNearbyMachinesRenderable(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (!CrossTieConfig.enableRenderCulling || this.worldObj == null || !this.worldObj.isRemote) {
            return;
        }

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks <= 0) {
            return;
        }

        net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
        if (player == null) {
            return;
        }

        double forceRenderDistance = (renderChunks + 1) * 16.0D;
        double forceRenderDistanceSq = forceRenderDistance * forceRenderDistance;
        if (player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= forceRenderDistanceSq) {
            cir.setReturnValue(INFINITE_EXTENT_AABB);
        }
    }
}
