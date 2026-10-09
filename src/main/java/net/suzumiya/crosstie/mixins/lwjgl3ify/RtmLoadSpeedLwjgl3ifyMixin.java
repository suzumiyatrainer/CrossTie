package net.suzumiya.crosstie.mixins.lwjgl3ify;

import jp.ngt.rtm.RTMConfig;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * lwjgl3ify 環境で RTM の {@code ModelPack} ロード速度を "Fast (3)" から
 * "Default (2)" にクランプするパッチ。
 *
 * <h3>問題の概要</h3>
 * RTM の {@code loadSpeed=3} は Work-Stealing プールを使った並列ロードを行う。
 * lwjgl3ify 環境ではこのモードが不安定でクラッシュが発生しやすいことが
 * LWJGL3ify-rtm-1.0.0 の調査で判明している。
 *
 * <h3>修正方針</h3>
 * {@code RTMConfig.syncConfig()} の末尾で {@code loadSpeed} が 3 以上なら 2 に
 * 書き戻す。{@code syncConfig()} は RTM の preInit が {@code ModelPackLoadThread}
 * を開始する前に同期的に実行されるため、ロードスレッドが値を読み取る前に確実に
 * 反映される。
 *
 * <h3>既存パッチとの関係</h3>
 * {@link net.suzumiya.crosstie.mixins.kaizpatch.ModelPackLoadSpeedMixin} は
 * loadSpeed が 1 以下の場合に 2 へ引き上げる（最低速保証）。
 * 本 Mixin は逆方向で、lwjgl3ify 存在時に 3 を 2 へ引き下げる（最高速制限）。
 * 両者は競合せず補完関係にある。
 *
 * <h3>適用条件</h3>
 * {@code lwjgl3ify && RTM} の場合のみ
 * {@link net.suzumiya.crosstie.mixins.CrossTieMixinPlugin} が登録する。
 *
 * <p><b>原作:</b> LWJGL3ify-rtm-1.0.0 by 325 (LGPL-3.0-or-later)<br>
 * 本ファイルは LWJGL3ify-rtm より派生し CrossTie 向けに Mixin 形式へ改変したものです。
 */
@Mixin(value = RTMConfig.class, remap = false)
public abstract class RtmLoadSpeedLwjgl3ifyMixin {

    /**
     * {@code syncConfig()} の全 return 直前に実行し {@code loadSpeed >= 3} を 2 に丸める。
     *
     * <p>Mixin の仕様上、static メソッドへの {@code @Inject} コールバックも
     * {@code static} でなければならない。
     */
    @Inject(method = "syncConfig", at = @At("RETURN"))
    private static void crosstie$clampLoadSpeedForLwjgl3ify(CallbackInfo ci) {
        if (RTMConfig.loadSpeed >= 3) {
            CrossTie.LOGGER.info(
                    "[CrossTie] RTM ModelPack loadSpeed を {} (Fast) から 2 (Default) にクランプしました。" +
                    " lwjgl3ify 環境では Fast モードが不安定なため。",
                    RTMConfig.loadSpeed);
            RTMConfig.loadSpeed = 2;
        }
    }
}

