package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.suzumiya.crosstie.compat.lwjgl3ify.ImeCompat;

/** lwjgl3ify 環境で IME の自動有効化・候補窓位置通知を毎 tick 実行する。 */
public final class ImeTicker {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ImeCompat.tick();
        }
    }
}
