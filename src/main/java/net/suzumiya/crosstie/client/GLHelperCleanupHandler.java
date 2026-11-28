package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.GLHelper;
import net.minecraftforge.event.world.WorldEvent;

import java.lang.reflect.Field;

/**
 * {@link GLHelper} の遅延 DisplayList 解放および OptiFine ShadersMod シェーダー切替連動ハンドラ。
 *
 * <ul>
 *   <li>通常フレーム末 ({@code RenderTickEvent.Phase.END}): 最大 64 件ずつ分散解放しスパイクを防止。</li>
 *   <li>クライアントワールドアンロード ({@code WorldEvent.Unload}): 次のレンダーティックで全件解放。</li>
 *   <li>OptiFine ShadersMod: {@code shadersmod.client.Shaders.numberResetDisplayList} 変化を検知して
 *       {@link GLHelper#initGLList()} を呼び、シェーダー切替時の頂点属性不整合を防止。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class GLHelperCleanupHandler {

    private static final int MAX_DELETE_PER_FRAME = 64;

    private static volatile boolean pendingFlushAll = false;

    private static boolean shaderFieldChecked = false;
    private static Field optifineResetCounterField = null;
    private static int lastShaderResetCount = Integer.MIN_VALUE;

    private static volatile Runnable batchClearCallback;
    private static volatile Runnable flushAllCallback;

    public static void registerCallbacks(Runnable batchClear, Runnable flushAll) {
        batchClearCallback = batchClear;
        flushAllCallback = flushAll;
    }

    public static int getMaxDeletePerFrame() {
        return MAX_DELETE_PER_FRAME;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            checkOptiFineShaderReset();
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            if (GLHelper.isCompiling()) {
                return;
            }
            if (pendingFlushAll) {
                pendingFlushAll = false;
                Runnable flush = flushAllCallback;
                if (flush != null) {
                    flush.run();
                } else {
                    GLHelper.clearGLList();
                }
            } else {
                Runnable batch = batchClearCallback;
                if (batch != null) {
                    batch.run();
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null && event.world.isRemote) {
            pendingFlushAll = true;
        }
    }

    private static void checkOptiFineShaderReset() {
        if (!shaderFieldChecked) {
            shaderFieldChecked = true;
            try {
                Class<?> shadersClass = Class.forName("shadersmod.client.Shaders");
                Field f = shadersClass.getDeclaredField("numberResetDisplayList");
                f.setAccessible(true);
                optifineResetCounterField = f;
                lastShaderResetCount = f.getInt(null);
            } catch (Throwable ignored) {
                optifineResetCounterField = null;
            }
            return;
        }

        if (optifineResetCounterField != null) {
            try {
                int current = optifineResetCounterField.getInt(null);
                if (current != lastShaderResetCount) {
                    lastShaderResetCount = current;
                    if (!GLHelper.isCompiling()) {
                        GLHelper.initGLList();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
