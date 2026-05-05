package net.suzumiya.crosstie.mixins.webctc;

import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WebCTC の WebSocket 全送信頻度を監視し、過剰送信を警告する。
 *
 * <p>audit §4.4 / §9 P1:
 * "WebSocket 全送信を dirty diff に変える。" — の事前観測フェーズとして、
 * 毎 tick の {@code sendAll()} 呼び出しをカウントし、閾値超過時に警告する。
 *
 * <p>本 Mixin は実際の送信をキャンセルせず、計測と警告のみを行う (Phase 1 対応)。
 * dirty diff 化は WebCTC の送信コードへの大きな変更が必要なため、別フェーズで実装する。
 */
@Mixin(targets = "org.webctc.signal.SignalStateWS$Companion", remap = false)
public abstract class WebCtcWsSendMonitorMixin {

    /** 1分間あたりの sendAll() 呼び出しが何回を超えたら警告するか。 */
    private static final long WARN_CALLS_PER_MINUTE = 1200L; // 20 TPS * 60s

    private static long crosstie$sendCallCount = 0;
    private static long crosstie$lastWarnTime = 0;

    @Inject(method = "sendAll", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$countWsSendAll(CallbackInfo ci) {
        crosstie$sendCallCount++;

        long now = System.currentTimeMillis();
        long elapsed = now - crosstie$lastWarnTime;

        if (elapsed >= 60_000L) {
            if (crosstie$sendCallCount > WARN_CALLS_PER_MINUTE) {
                CrossTie.LOGGER.warn(
                        "[CrossTie] SignalStateWS.sendAll() was called {} times in the last {}ms. "
                                + "Consider switching to dirty-diff WebSocket updates to reduce network load.",
                        crosstie$sendCallCount, elapsed);
            }
            crosstie$sendCallCount = 0;
            crosstie$lastWarnTime = now;
        }
    }
}
