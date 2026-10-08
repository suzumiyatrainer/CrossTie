package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.suzumiya.crosstie.utils.CrossTieDiagnostics;

/** Emits the opt-in CrossTie diagnostic snapshot once per elapsed minute. */
public final class CrossTieDiagnosticsTicker {

    private static final long REPORT_INTERVAL_NANOS = 60_000_000_000L;
    private long lastReportNanos;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!CrossTieDiagnostics.isEnabled()) {
            lastReportNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (lastReportNanos == 0L) {
            lastReportNanos = now;
            return;
        }

        if (now - lastReportNanos >= REPORT_INTERVAL_NANOS) {
            lastReportNanos = now;
            CrossTieDiagnostics.logAndReset();
        }
    }
}
