package net.suzumiya.crosstie.compat.lwjgl3ify;

import java.util.function.Consumer;

import me.eigenraven.lwjgl3ify.api.DisplayEvents;
import me.eigenraven.lwjgl3ify.api.DisplayWindowContext;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import me.eigenraven.lwjgl3ify.client.MainThreadExec;

import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilterI;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDL_TextEditingCandidatesEvent;
import org.lwjgl.sdl.SDL_TextEditingEvent;
import org.lwjgl.system.MemoryStack;
import org.lwjglx.opengl.Display;

/**
 * {@link Lwjgl3Aware} により {@code LwjglRedirectTransformer} のリマップ対象から除外された
 * SDL3 ネイティブ IME ブリッジ。
 *
 * <ul>
 * <li>{@code SDL_HINT_IME_IMPLEMENTED_UI = "composition,candidates"} を設定し、
 * Windows IMM32 の古い白枠コンポジション窓(MS ゴシックの「あ」小窓)を抑止する。</li>
 * <li>{@code SDL_AddEventWatch} で {@code SDL_EVENT_TEXT_EDITING} および
 * {@code SDL_EVENT_TEXT_EDITING_CANDIDATES} を捕捉し、Minecraft 側のフォント
 * ({@code FontRenderer} / MinFo 等)でインライン変換表示・候補窓を描画できるようにする。</li>
 * </ul>
 */
@Lwjgl3Aware
public final class SdlImeBridge implements SDL_EventFilterI, Consumer<DisplayWindowContext>, Runnable {

    public static final SdlImeBridge INSTANCE = new SdlImeBridge();

    private static volatile boolean preWindowHookRegistered = false;
    private static volatile boolean watchRegistered = false;

    private SdlImeBridge() {}

    /**
     * Mixin 初期化時など {@code Display.create()} より前の段階で呼び出し、
     * ウィンドウ生成直前に IME ヒントと EventWatch を登録する。
     */
    public static void registerEarlyWindowHook() {
        if (preWindowHookRegistered) {
            return;
        }
        preWindowHookRegistered = true;
        try {
            DisplayEvents.addPreWindowCreateListener(INSTANCE);
        } catch (Throwable ignored) {}
    }

    /**
     * {@code Display.create()} 内 ({@code SDL_Init} 直後・{@code SDL_CreateWindowWithProperties} 直前) に
     * メインスレッド上で呼ばれるコールバック。
     */
    @Override
    public void accept(DisplayWindowContext ctx) {
        setupOnMainThread();
    }

    /** {@code MainThreadExec.runOnMainThread(INSTANCE)} 用エントリ。 */
    @Override
    public void run() {
        setupOnMainThread();
    }

    /**
     * 既にウィンドウが生成済みの場合も含め、メインスレッド上で IME ヒントと EventWatch を確実に有効化する。
     * ※Display.create() より前の段階では MainThreadExec がデッドロックするため、
     * ウィンドウ生成前は registerEarlyWindowHook() のみ登録し、Display.create() 側の
     * firePreWindowCreate() に実行を委ねる。
     */
    public static void ensureInitialized() {
        if (watchRegistered) {
            return;
        }
        registerEarlyWindowHook();
        try {
            if (Display.isCreated()) {
                MainThreadExec.runOnMainThread(INSTANCE);
            }
        } catch (Throwable ignored) {}
    }

    private static void setupOnMainThread() {
        try {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_IME_IMPLEMENTED_UI, "composition,candidates");
            if (!watchRegistered) {
                SDLEvents.SDL_AddEventWatch(INSTANCE, 0L);
                watchRegistered = true;
            }
        } catch (Throwable ignored) {}
    }

    /**
     * SDL イベントキューへイベントがプッシュされる際に呼ばれるネイティブコールバック。
     */
    @Override
    public boolean invoke(long userdata, long event) {
        if (event == 0L) {
            return true;
        }
        try {
            int type = SDL_Event.ntype(event);
            if (type == SDLEvents.SDL_EVENT_TEXT_EDITING) {
                String text = SDL_TextEditingEvent.ntextString(event);
                int start = SDL_TextEditingEvent.nstart(event);
                int length = SDL_TextEditingEvent.nlength(event);
                ImeCompat.onTextEditing(text != null ? text : "", start, length);
            } else if (type == SDLEvents.SDL_EVENT_TEXT_EDITING_CANDIDATES) {
                int num = SDL_TextEditingCandidatesEvent.nnum_candidates(event);
                int selected = SDL_TextEditingCandidatesEvent.nselected_candidate(event);
                boolean horizontal = SDL_TextEditingCandidatesEvent.nhorizontal(event);
                if (num > 0) {
                    PointerBuffer ptrs = SDL_TextEditingCandidatesEvent.ncandidates(event);
                    if (ptrs != null) {
                        String[] list = new String[num];
                        for (int i = 0; i < num; i++) {
                            long addr = ptrs.get(i);
                            String s = org.lwjgl.system.MemoryUtil.memUTF8Safe(addr);
                            list[i] = s != null ? s : "";
                        }
                        ImeCompat.onTextEditingCandidates(list, selected, horizontal);
                    } else {
                        ImeCompat.onTextEditingCandidates(null, -1, false);
                    }
                } else {
                    ImeCompat.onTextEditingCandidates(null, -1, false);
                }
            } else if (type == SDLEvents.SDL_EVENT_TEXT_INPUT) {
                ImeCompat.onTextCommitted();
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public static long getWindow() {
        return Display.getWindow();
    }

    public static boolean isTextInputActive(long window) {
        return window != 0L && SDLKeyboard.SDL_TextInputActive(window);
    }

    public static void startTextInput(long window) {
        if (window != 0L) {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_IME_IMPLEMENTED_UI, "composition,candidates");
            SDLKeyboard.SDL_StartTextInput(window);
        }
    }

    public static void stopTextInput(long window) {
        if (window != 0L) {
            SDLKeyboard.SDL_StopTextInput(window);
        }
    }

    public static void updateTextInputArea(long window, int fbX, int fbY, int fbW, int fbH, int fbCursor) {
        if (window == 0L) {
            return;
        }
        float density = SDLVideo.SDL_GetWindowPixelDensity(window);
        if (density <= 0f) {
            density = 1f;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_Rect.Buffer rb = SDL_Rect.malloc(1, stack);
            SDL_Rect r = rb.get(0);
            r.x(Math.round(fbX / density));
            r.y(Math.round(fbY / density));
            r.w(Math.max(1, Math.round(fbW / density)));
            r.h(Math.max(1, Math.round(fbH / density)));
            SDLKeyboard.SDL_SetTextInputArea(window, rb, Math.max(0, Math.round(fbCursor / density)));
        }
    }
}
