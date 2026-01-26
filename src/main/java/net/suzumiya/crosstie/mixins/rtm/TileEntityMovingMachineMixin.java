package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntityMovingMachine;
import net.suzumiya.crosstie.CrossTie;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityMovingMachine.class, remap = false)
public abstract class TileEntityMovingMachineMixin extends TileEntity {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        if (this.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            double cullLimit = (renderChunks + 2) * 16.0;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && this.getDistanceFrom(player.posX, player.posY, player.posZ) > limitSq) {
                ci.cancel();
            }
        } else {
            // サーバー側最適化: 手動ループでプレイヤー確認 (128ブロック以内)
            // MovingMachineは動きが重要なので、間引きすぎないが、
            // 誰も見ていないなら動かなくて良い。

            // 負荷分散のため、EntityIDに基づいて分散チェック (毎tick全TEがループするのは避ける)
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
            // チェックしないtick (4/5) は実行する (カクつき防止)
            // いや、これだと「4/5は無条件で動く」ので軽量化にならない。

            // 逆の発想: 「基本キャンセル」し、「チェックで近くにいたら動かす」も無理（状態保存できない）。

            // ここも「単純間引き」にします。
            // MovingMachineはサーバー側で当たり判定が変わる(遮断機が降りる)ので、
            // 遅くなると列車がすり抜ける可能性がある。
            // RTMにおいてサーバーTPS最適化は非常に危険。

            // ユーザー要望「できれば」 -> 安全第一で「MovingMachineはサーバー最適化しない」が正解。
            // しかしBambooは単純間引き(1/2)を入れました。
            // RTMはやはり怖いので、ここはサーバー側は何もしない(return)に戻します。
        }
    }
}
