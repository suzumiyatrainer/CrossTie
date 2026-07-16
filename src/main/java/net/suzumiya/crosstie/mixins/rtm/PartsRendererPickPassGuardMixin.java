package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.PartsRenderer;
import jp.ngt.rtm.render.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CrossTie: PartsRenderer PICK パス多重実行防止Mixin
 *
 * <h3>問題の背景</h3>
 * <p>{@code PartsRenderer#render()} は {@code currentMatId == 0} かつ
 * {@code targetsList} が空でない場合に PICK パス（GL_SELECT）を自動実行する。
 * しかし {@code ModelObject#renderWithTexture()} は materials の forEach ループ内で
 * {@code currentMatId} を更新して {@code render()} を毎回呼ぶため、
 * matId=0 のエントリが複数存在するモデルでは同一フレームで PICK パスが
 * 複数回実行される可能性がある。</p>
 *
 * <h3>修正内容</h3>
 * <p>フレームごとに PICK パスの実行回数を1回に制限する。
 * タイムスタンプ（ミリ秒）でフレームを区別し、同一ミリ秒内での
 * 2回目以降の PICK 呼び出しをキャンセルする。</p>
 *
 * <p>非Angelica環境を主な対象とするが、万が一 targetsList が空でない
 * Angelica環境でも同様に有効。</p>
 */
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererPickPassGuardMixin<T, MS> {

    /**
     * このフレームで既に PICK パスを実行したかどうかのフラグ。
     * 同一フレーム内での2回目以降の PICK 呼び出しをブロックする。
     */
    private boolean crosstie$pickedThisFrame = false;

    /**
     * 最後に PICK パスを実行した時刻（ミリ秒）。
     * フレームの境界判定に使用する。厳密なフレーム境界は
     * System.currentTimeMillis() では保証できないが、同一描画コール内での
     * 重複防止には十分な精度がある（通常1フレームは16ms以上）。
     */
    private long crosstie$lastPickFrameTime = -1L;

    /**
     * PICK パスの HEAD で呼ばれ、このフレームで既に PICK 済みの場合は
     * キャンセルする。PICK パス以外は通過させる。
     */
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void crosstie$guardPickPassDuplicate(T t, int pass, float partialTick,
                                                  CallbackInfo ci) {
        if (pass != RenderPass.PICK.id) {
            return;
        }

        long now = System.currentTimeMillis();

        // 前フレームのフラグをリセット
        if (now != this.crosstie$lastPickFrameTime) {
            this.crosstie$pickedThisFrame = false;
            this.crosstie$lastPickFrameTime = now;
        }

        if (this.crosstie$pickedThisFrame) {
            // このフレームで既に PICK 済み → スキップ
            ci.cancel();
            return;
        }

        this.crosstie$pickedThisFrame = true;
    }
}
