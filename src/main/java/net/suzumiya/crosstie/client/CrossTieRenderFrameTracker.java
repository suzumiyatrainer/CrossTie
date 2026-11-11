package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

/**
 * クライアントのレンダリングフレームを追跡するトラッカー。
 * <p>
 * 各フレームの描画開始時（{@link TickEvent.RenderTickEvent.Phase#START}）にフレームカウントをインクリメントし、
 * 同一フレーム内での Pass 0 / Pass 1 などの複数レンダリングパスで一貫したフレーム番号を提供します。
 */
@SideOnly(Side.CLIENT)
public final class CrossTieRenderFrameTracker {

    private static int renderFrameCount = 0;
    private static boolean eventReceived = false;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            renderFrameCount++;
            eventReceived = true;
        }
    }

    /**
     * 現在の描画フレーム番号を取得します。
     * イベント未受信時はフォールバックとして ticksExisted を返します。
     */
    public static int getRenderFrame() {
        if (!eventReceived) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.renderViewEntity != null) {
                return mc.renderViewEntity.ticksExisted;
            }
        }
        return renderFrameCount;
    }
}

