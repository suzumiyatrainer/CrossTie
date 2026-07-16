package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.accessors.rtm.IEntityVehicleBaseRenderContextAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CrossTie: EntityVehicleBase#onModelChanged() 描画中抑制Mixin
 *
 * <h3>問題の背景</h3>
 * <p>RTMでは {@code RenderVehicleBase#doRender()} から毎フレーム
 * {@code vehicle.getModelSet()} が呼ばれる。このメソッド内では
 * DataWatcher（非同期パケット受信で更新）経由で取得したモデル名と
 * 現在保持しているモデルセットを比較し、不一致の場合に {@code onModelChanged()}
 * を呼び出す。</p>
 *
 * <p>DataWatcherはサーバーからのパケット受信タイミングによって任意のフレームで
 * 別エンティティのモデル名を一時的に参照してしまう可能性がある。
 * その瞬間に {@code onModelChanged()} が誤発火し、クライアント側の音響・
 * アニメーション・描画データが別モデルのものに混線する。</p>
 *
 * <h3>修正内容</h3>
 * <p>描画コンテキスト中（{@code doRender()} 実行中）は
 * {@code onModelChanged()} をキャンセルする。
 * {@code getModelSet()} そのものは通常通り動作させ、
 * 描画には最新の modelSet が使われることを保証する。</p>
 *
 * <p><b>Angelica環境でも有効。PICK パスの有無に依存しない。</b></p>
 */
@Mixin(value = EntityVehicleBase.class, remap = false)
public abstract class EntityVehicleBaseModelSetGuardMixin
        implements IEntityVehicleBaseRenderContextAccessor {

    /**
     * 描画スレッドから getModelSet() が呼ばれているかどうかのフラグ。
     * true の間は onModelChanged() を抑制する。
     * {@code RenderVehicleBaseContextMixin} が doRender の前後でセット/クリアする。
     */
    private boolean crosstie$isInRenderContext = false;

    @Override
    public boolean crosstie$isInRenderContext() {
        return this.crosstie$isInRenderContext;
    }

    @Override
    public void crosstie$setInRenderContext(boolean value) {
        this.crosstie$isInRenderContext = value;
    }

    /**
     * 描画コンテキスト中に {@code onModelChanged()} が呼ばれた場合、
     * 音響アップデート等の副作用をキャンセルする。
     *
     * <p>ただし {@code getModelSet()} そのものはキャンセルしないため、
     * 描画フレームには正しい（あるいはその時点での）modelSet が使われる。
     * 副作用だけを抑制することで混線を防ぐ。</p>
     */
    @Inject(method = "onModelChanged", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$suppressOnModelChangedInRenderContext(CallbackInfo ci) {
        if (this.crosstie$isInRenderContext) {
            ci.cancel();
        }
    }
}
