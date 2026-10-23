package net.suzumiya.crosstie.compat.lwjgl3ify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

/**
 * lwjgl3ify (SDL3) 環境における日本語IMEの汎用補正。Mod 個別の対応ではなく全 Mod 共通で効く。
 *
 * <ol>
 * <li>lwjgl3ify は {@code GuiTextField.setFocused} 等の特定箇所でのみ {@code SDL_StartTextInput}
 * を呼ぶため、独自テキスト欄を持つ Mod(JourneyMap 等)ではIMEが有効にならない。
 * → GUI 画面が開いている間は TextInput を自動で有効化する(GuiContainer は誤爆防止のため除外)。</li>
 * <li>lwjgl3ify は {@code SDL_SetTextInputArea} を一切呼ばないため、変換候補窓が左上に固定される。
 * → フォーカス中の GuiTextField、無ければ最後のクリック位置を入力エリアとして通知する。</li>
 * <li>TextInput 有効化により ASCII がキーイベント由来とテキストイベント由来で二重に届くため、
 * 直近のキーイベント文字と一致するテキストイベント文字を破棄する。</li>
 * </ol>
 *
 * <p>
 * 無効化: JVM 引数 {@code -Dcrosstie.disableImeFix=true}
 */
public final class ImeCompat {

    private static final long DUP_WINDOW_NS = 300_000_000L;
    private static final boolean DISABLED = Boolean.getBoolean("crosstie.disableImeFix");

    private static Method getWindowMethod;
    private static Method execMethod;
    private static boolean available;

    static {
        try {
            getWindowMethod = Class.forName("org.lwjglx.opengl.Display")
                .getMethod("getWindow");
            execMethod = Class.forName("me.eigenraven.lwjgl3ify.client.MainThreadExec")
                .getMethod("runOnMainThread", Runnable.class);
            available = !DISABLED;
        } catch (Throwable t) {
            available = false;
        }
    }

    // ---- 二重入力除去 ---- //

    private static final ArrayDeque<long[]> recentKeyChars = new ArrayDeque<>();

    /** キーイベントで配送した文字を記録する。 */
    public static void recordKeyChar(int c) {
        if (!available || c < 32) {
            return;
        }
        synchronized (recentKeyChars) {
            long now = System.nanoTime();
            prune(now);
            recentKeyChars.addLast(new long[] { c, now });
        }
    }

    /** @return テキストイベント由来の文字がキーイベントで配送済みなら true(破棄すべき) */
    public static boolean consumeDuplicateChar(int c) {
        if (!available || c < 32) {
            return false;
        }
        synchronized (recentKeyChars) {
            prune(System.nanoTime());
            Iterator<long[]> it = recentKeyChars.iterator();
            while (it.hasNext()) {
                if (it.next()[0] == c) {
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private static void prune(long now) {
        while (!recentKeyChars.isEmpty() && now - recentKeyChars.peekFirst()[1] > DUP_WINDOW_NS) {
            recentKeyChars.pollFirst();
        }
    }

    // ---- クリック位置(フォールバック用) ---- //

    private static volatile int lastClickX = -1;
    private static volatile int lastClickY = -1;

    /** マウスボタン押下時に呼ぶ。フレームバッファ px 座標(上原点)で保持。 */
    public static void onClick(int mouseX, int mouseYBottomOrigin) {
        Minecraft mc = Minecraft.getMinecraft();
        lastClickX = mouseX;
        lastClickY = mc.displayHeight - mouseYBottomOrigin;
    }

    // ---- 毎tick処理 ---- //

    private static boolean startedByUs = false;
    private static int lastX = Integer.MIN_VALUE, lastY, lastW, lastH, lastCursor;
    private static GuiScreen lastScreen;
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new HashMap<>();

    public static void tick() {
        if (!available) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        final GuiScreen screen = mc.currentScreen;
        final long window;
        try {
            window = (Long) getWindowMethod.invoke(null);
        } catch (Throwable t) {
            return;
        }
        if (window == 0L) {
            return;
        }

        if (screen == null) {
            if (startedByUs) {
                startedByUs = false;
                runOnMain(() -> SDLKeyboard.SDL_StopTextInput(window));
            }
            lastX = Integer.MIN_VALUE;
            lastScreen = null;
            return;
        }
        if (screen != lastScreen) {
            lastScreen = screen;
            lastX = Integer.MIN_VALUE; // 再通知
        }

        final boolean forceStart = !(screen instanceof GuiContainer);
        final int[] rect = computeAreaFramebufferPx(mc, screen); // x,y,w,h,cursor (px)

        runOnMain(() -> {
            boolean active = SDLKeyboard.SDL_TextInputActive(window);
            if (!active && forceStart) {
                SDLKeyboard.SDL_StartTextInput(window);
                startedByUs = true;
                active = true;
            }
            if (!active) {
                return;
            }
            if (rect[0] == lastX && rect[1] == lastY && rect[2] == lastW && rect[3] == lastH
                && rect[4] == lastCursor) {
                return;
            }
            lastX = rect[0];
            lastY = rect[1];
            lastW = rect[2];
            lastH = rect[3];
            lastCursor = rect[4];
            float density = SDLVideo.SDL_GetWindowPixelDensity(window);
            if (density <= 0f) {
                density = 1f;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_Rect.Buffer rb = SDL_Rect.malloc(1, stack);
                SDL_Rect r = rb.get(0);
                r.x(Math.round(rect[0] / density));
                r.y(Math.round(rect[1] / density));
                r.w(Math.max(1, Math.round(rect[2] / density)));
                r.h(Math.max(1, Math.round(rect[3] / density)));
                SDLKeyboard.SDL_SetTextInputArea(window, rb, Math.round(rect[4] / density));
            }
        });
    }

    /** @return {x, y, w, h, cursorOffset} (フレームバッファ px、上原点) */
    private static int[] computeAreaFramebufferPx(Minecraft mc, GuiScreen screen) {
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int scale = Math.max(1, sr.getScaleFactor());

        GuiTextField focused = findFocusedTextField(screen);
        if (focused != null) {
            int cursorPx = 0;
            try {
                String text = focused.getText();
                int cp = Math.max(0, Math.min(text.length(), focused.getCursorPosition()));
                cursorPx = mc.fontRenderer.getStringWidth(text.substring(0, cp)) * scale;
            } catch (Throwable ignored) {}
            int w = focused.width * scale;
            return new int[] { focused.xPosition * scale, focused.yPosition * scale, w, focused.height * scale,
                Math.min(cursorPx, Math.max(0, w - 1)) };
        }

        int cx = lastClickX >= 0 ? lastClickX : mc.displayWidth / 2;
        int cy = lastClickY >= 0 ? lastClickY : mc.displayHeight / 2;
        return new int[] { cx, cy, 100 * scale / 2, 10 * scale, 0 };
    }

    private static GuiTextField findFocusedTextField(GuiScreen screen) {
        Field[] fields = FIELD_CACHE.get(screen.getClass());
        if (fields == null) {
            java.util.ArrayList<Field> list = new java.util.ArrayList<>();
            for (Class<?> c = screen.getClass(); c != null && c != GuiScreen.class
                && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || t.isPrimitive()) {
                        continue;
                    }
                    if (GuiTextField.class.isAssignableFrom(t) || Iterable.class.isAssignableFrom(t)
                        || t.isArray() && GuiTextField.class.isAssignableFrom(t.getComponentType())
                        || t == Object.class) {
                        try {
                            f.setAccessible(true);
                            list.add(f);
                        } catch (Throwable ignored) {}
                    }
                }
            }
            fields = list.toArray(new Field[0]);
            FIELD_CACHE.put(screen.getClass(), fields);
        }
        for (Field f : fields) {
            try {
                Object v = f.get(screen);
                GuiTextField hit = pick(v);
                if (hit != null) {
                    return hit;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static GuiTextField pick(Object v) {
        if (v instanceof GuiTextField) {
            return ((GuiTextField) v).isFocused() ? (GuiTextField) v : null;
        }
        if (v instanceof GuiTextField[]) {
            for (GuiTextField t : (GuiTextField[]) v) {
                if (t != null && t.isFocused()) {
                    return t;
                }
            }
        } else if (v instanceof Collection) {
            for (Object o : (Collection<?>) v) {
                if (o instanceof GuiTextField && ((GuiTextField) o).isFocused()) {
                    return (GuiTextField) o;
                }
            }
        }
        return null;
    }

    private static void runOnMain(Runnable r) {
        try {
            execMethod.invoke(null, r);
        } catch (Throwable ignored) {}
    }

    private ImeCompat() {}
}
