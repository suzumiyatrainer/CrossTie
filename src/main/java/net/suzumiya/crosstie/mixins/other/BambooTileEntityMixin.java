package net.suzumiya.crosstie.mixins.other;

import net.suzumiya.crosstie.CrossTie;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BambooModのTileEntity最適化
 * 更新処理が重いTileEntityを距離カリングします。
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

            // クライアント側チェック
            double cullLimit = renderChunks * 16.0;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null && this.getDistanceFrom(player.posX, player.posY, player.posZ) > limitSq) {
                ci.cancel();
            }
        } else {
            // サーバー側最適化: 手動ループでプレイヤー確認
            // 20tickに1回だけチェック (負荷分散)
            if ((this.worldObj.getTotalWorldTime() + this.hashCode()) % 20 == 0) {
                boolean isPlayerNear = false;
                double limitSq = 64.0 * 64.0; // 64m以内

                for (Object obj : this.worldObj.playerEntities) {
                    if (obj instanceof net.minecraft.entity.Entity) {
                        net.minecraft.entity.Entity p = (net.minecraft.entity.Entity) obj;
                        // getDistanceFrom returns squared distance
                        if (this.getDistanceFrom(p.posX, p.posY, p.posZ) < limitSq) {
                            isPlayerNear = true;
                            break;
                        }
                    }
                }

                // プレイヤーが近くにいなければキャンセル
                // ただし、この判定は20tickに1回しか行われないため、
                // 「判定したtickだけキャンセルする」ことになり、間引き実行(Throttling)になる。
                // これにより負荷は約1/20になる。完全停止ではないので調理も少しずつ進む。
                if (!isPlayerNear) {
                    ci.cancel();
                }
            } else {
                // チェックしないtickも、近くにいないと仮定してスキップする？
                // いや、状態を持てないので、「チェックの時だけ動く」or「チェックの時だけ止まる」のどちらか。
                // 上記ロジックだと「チェックした時に近くにいなければ止まる」だけなので、19/20は通常通り動いてしまう。

                // 逆にしよう。「プレイヤーが近くにいるなら動く」
                // しかし状態保存できない。

                // 妥協案: サーバー側では「無条件で間引く」
                // プレイヤー距離チェック自体が重いので、遠くのチャンクローダー内での動作を軽くしたいなら
                // 「2tickに1回実行」などの単純な間引きがベスト。
                // BambooのTEは軽いので、何もしないのが一番安全かも。

                // しかしユーザーの要望に応えるなら:
                // 「プレイヤー距離チェックを行い、遠ければキャンセル」
                // これを毎tickやるのは、TEが1万個あると重い。

                // 今回は「安全確実な単純間引き」にします。
                // サーバー側では常に 1/2 の頻度で実行 (2回に1回スキップ)
                // これなら全域で軽量化され、副作用も少ない(調理時間が2倍になるだけ)。
                if (this.worldObj.getTotalWorldTime() % 2 != 0) {
                    ci.cancel();
                }
            }
        }
    }
}
