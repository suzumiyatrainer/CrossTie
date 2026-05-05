package net.suzumiya.crosstie.mixins.webctc;

import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WebCTC の RailGroupStateWS 全送信頻度を監視し、過剰送信を警告する。
 *
 * <p>audit §4.4 / §9 P1:
 * "WebSocket 全送信を dirty diff に変える。"
 *
 * <p>{@link WebCtcWsSendMonitorMixin} と同様のアプローチで、
 * RailGroupStateWS.sendAll() を監視する。
 */
@Mixin(targets = "org.webctc.railgroup.RailGroupStateWS$Companion", remap = false)
public abstract class WebCtcRailGroupWsMonitorMixin {

    private static final long WARN_CALLS_PER_MINUTE = 1200L;

    private static long crosstie$railGroupSendCount = 0;
    private static long crosstie$lastWarnTime = 0;

    @Inject(method = "sendAll", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$countRailGroupSendAll(CallbackInfo ci) {
        crosstie$railGroupSendCount++;

        long now = System.currentTimeMillis();
        long elapsed = now - crosstie$lastWarnTime;

        if (elapsed >= 60_000L) {
            if (crosstie$railGroupSendCount > WARN_CALLS_PER_MINUTE) {
                CrossTie.LOGGER.warn(
                        "[CrossTie] RailGroupStateWS.sendAll() was called {} times in the last {}ms. "
                                + "Consider switching to dirty-diff WebSocket updates.",
                        crosstie$railGroupSendCount, elapsed);
            }
            crosstie$railGroupSendCount = 0;
            crosstie$lastWarnTime = now;
        }
    }
}
