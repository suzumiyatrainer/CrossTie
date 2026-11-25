package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.suzumiya.crosstie.compat.lwjgl3ify.ImeCompat;

/**
 * lwjgl3ify 環境で IME の自動有効化・候補窓位置通知・インライン変換オーバーレイ描画を実行する。
 */
public final class ImeTicker {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ImeCompat.tick();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ImeCompat.beginFrame();
        }
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui != null) {
            ImeCompat.tick();
            ImeCompat.renderOverlay(event.gui);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui == null) {
            ImeCompat.onScreenClosed();
        } else {
            ImeCompat.onTextCommitted();
            ImeCompat.clearLwjgl3ifyTextBuffer();
        }
    }
}
