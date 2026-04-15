package net.suzumiya.crosstie.mixins.bamboo;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bamboo Mod の TileEntity 更新を距離で抑制する。
 *
 * クライアント側は描画範囲外の更新を止め、サーバー側は更新頻度を落として負荷を下げる。
 */
@Mixin(targets = {
        "ruby.bamboo.tileentity.TileEntityCampfire",
        "ruby.bamboo.tileentity.TileEntityAndon",
        "ruby.bamboo.tileentity.TileEntityManeki",
        "ruby.bamboo.tileentity.TileEntityMillStone",
        "ruby.bamboo.tileentity.TileEntityMultiPot",
        "ruby.bamboo.tileentity.TileEntitySpaParent",
        "ruby.bamboo.tileentity.TileEntityMultiBlock",
        "ruby.bamboo.tileentity.TileEntityVillagerBlock"
}, remap = false)
public abstract class BambooTileEntityMixin extends TileEntity {

    @Inject(method = { "updateEntity", "func_145845_h" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullUpdate(CallbackInfo ci) {
        if (this.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            // クライアント側は描画距離 + 2 チャンクで間引く
            double cullLimit = renderChunks * 16.0;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && this.getDistanceFrom(player.posX, player.posY, player.posZ) > limitSq) {
                ci.cancel();
            }
        } else {
            // サーバー側は更新を半分に抑え、必要時だけ近くのプレイヤーを確認する
            if ((this.worldObj.getTotalWorldTime() + this.hashCode()) % 20 == 0) {
                boolean isPlayerNear = false;
                double limitSq = 64.0 * 64.0;
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
            } else {
                if (this.worldObj.getTotalWorldTime() % 2 != 0) {
                    ci.cancel();
                }
            }
        }
    }
}
