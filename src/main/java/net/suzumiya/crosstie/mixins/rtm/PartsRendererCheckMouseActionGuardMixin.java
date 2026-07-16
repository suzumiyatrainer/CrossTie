package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.PartsRenderer;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CrossTie: PartsRenderer#checkMouseAction() ホバー時副作用ガードMixin
 *
 * <h3>問題の背景</h3>
 * <p>{@code PartsRenderer#render()} は NORMAL パスが来るたびに PICK パス
 * （GL_SELECT モード）を自動実行し、その結果を {@code hittedEntity}/
 * {@code hittedParts} に即座に書き込む。フレーム内で複数エンティティが
 * 順番に描画されると後発エンティティのデータで上書きされ、続けて呼ばれる
 * {@code checkMouseAction()} が誤ったエンティティのスクリプト
 * ({@code onRightClick}/{@code onRightDrag}) を発火してしまう。</p>
 *
 * <h3>修正内容</h3>
 * <p>マウスボタンが一切押されていない（＝ホバーのみ）の場合、
 * {@code checkMouseAction()} の実行を先頭でキャンセルする。
 * これによりホバー中のスクリプト誤発火を完全に防ぐ。</p>
 *
 * <p>主に非Angelica環境（targetsList が空でない場合）の PICK パス問題を
 * 対象とするが、Angelica環境でも適用して害はない。</p>
 */
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererCheckMouseActionGuardMixin {

    /**
     * マウスボタンが一切押されていない（ホバーのみ）場合は
     * {@code checkMouseAction} を即座にキャンセルする。
     *
     * <p>右クリック（button=1）も左クリック（button=0）も
     * 押されていない場合のみキャンセルし、正常なクリック操作は
     * 元の動作のまま通す。</p>
     */
    @Inject(
        method = "checkMouseAction",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void crosstie$guardCheckMouseAction(Object t, CallbackInfo ci) {
        if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) {
            ci.cancel();
        }
    }
}
