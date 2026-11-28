package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntitySignBoard;
import jp.ngt.rtm.modelpack.texture.SignBoardProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3-5: {@link TileEntitySignBoard#updateEntity()} の不要な毎 tick RS チェック・ライト再計算を回避する。
 *
 * <p>{@code prop.lightValue >= 0} の通常看板では {@code isGettingPower} が一切参照されないため、
 * サーバー側では即スキップし、クライアント側も非アニメーション ({@code prop.frame <= 1}) なら即スキップする。
 * {@code canUpdate() = true} を維持しているため、後からの動的テクスチャ変更にも 100% 追従する。</p>
 */
@Mixin(TileEntitySignBoard.class)
public abstract class TileEntitySignBoardOptimizationMixin {

    @Shadow(remap = false)
    public abstract SignBoardProperty getProperty();

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$skipStaticSignBoardTick(CallbackInfo ci) {
        TileEntitySignBoard self = (TileEntitySignBoard) (Object) this;
        if (self.getWorldObj() == null) {
            return;
        }
        SignBoardProperty prop = this.getProperty();
        if (prop == null) {
            return;
        }
        if (prop.lightValue >= 0 && (!self.getWorldObj().isRemote || prop.frame <= 1)) {
            ci.cancel();
        }
    }
}
