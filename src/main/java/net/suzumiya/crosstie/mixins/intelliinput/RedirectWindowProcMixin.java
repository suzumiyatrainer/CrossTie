package net.suzumiya.crosstie.mixins.intelliinput;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.suzumiya.crosstie.CrossTie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stabilizes IntelliInput's native callback path.
 *
 * IntelliInput may throw LastErrorException(87) from CallWindowProc in some
 * render/input stacks. To keep the game alive, bypass its chained
 * CallWindowProc and route callback handling to DefWindowProc.
 */
@Pseudo
@Mixin(targets = "com.tsoft_web.IntelliInput.RedirectWindowProc", remap = false)
public abstract class RedirectWindowProcMixin {

    private static final AtomicBoolean LOGGED_ONCE = new AtomicBoolean(false);
    private static volatile Method CALL_WINDOW_PROC_INT_METHOD;
    private static volatile Method CALL_WINDOW_PROC_PTR_METHOD;
    private static volatile Method DEF_WINDOW_PROC_METHOD;

    @Redirect(
            method = "CommitIMEComposition",
            at = @At(value = "INVOKE", target = "Lcom/tsoft_web/IntelliInput/IntelliInput;sendNullKeydown()V"),
            remap = false)
    private static void crosstie$guardNullKeydownDuringChat() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.currentScreen instanceof GuiChat) {
            return;
        }
        try {
            Class<?> intelliInputClass =
                    Class.forName("com.tsoft_web.IntelliInput.IntelliInput", false,
                            RedirectWindowProcMixin.class.getClassLoader());
            Method method = intelliInputClass.getDeclaredMethod("sendNullKeydown");
            method.setAccessible(true);
            method.invoke(null);
        } catch (Throwable t) {
            crosstie$logOnce("CrossTie: IntelliInput sendNullKeydown() fallback failed.", t);
        }
    }

    @Redirect(
            method = "callback",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tsoft_web/IntelliInput/User32_Callback;CallWindowProc(ILcom/sun/jna/platform/win32/WinDef$HWND;ILcom/sun/jna/platform/win32/WinDef$WPARAM;Lcom/sun/jna/platform/win32/WinDef$LPARAM;)Lcom/sun/jna/platform/win32/WinDef$LRESULT;"),
            remap = false)
    @Coerce
    private Object crosstie$guardCallWindowProcInt(
            @Coerce Object user32,
            int prevWndProc,
            @Coerce Object hWnd,
            int message,
            @Coerce Object wParam,
            @Coerce Object lParam) {
        try {
            Method method = crosstie$findCallWindowProcMethod(user32.getClass(), true);
            if (method == null) {
                return crosstie$callDefWindowProc(user32, hWnd, message, wParam, lParam);
            }
            return method.invoke(
                    user32,
                    Integer.valueOf(prevWndProc),
                    hWnd,
                    Integer.valueOf(message),
                    wParam,
                    lParam);
        } catch (Throwable t) {
            crosstie$logOnce("CrossTie: IntelliInput CallWindowProc(int) failed, using DefWindowProc.", t);
            return crosstie$callDefWindowProc(user32, hWnd, message, wParam, lParam);
        }
    }

    @Redirect(
            method = "callback",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lcom/tsoft_web/IntelliInput/User32_Callback;CallWindowProc(Lcom/sun/jna/platform/win32/BaseTSD$LONG_PTR;Lcom/sun/jna/platform/win32/WinDef$HWND;ILcom/sun/jna/platform/win32/WinDef$WPARAM;Lcom/sun/jna/platform/win32/WinDef$LPARAM;)Lcom/sun/jna/platform/win32/WinDef$LRESULT;"),
            remap = false)
    @Coerce
    private Object crosstie$guardCallWindowProcPtr(
            @Coerce Object user32,
            @Coerce Object prevWndProc,
            @Coerce Object hWnd,
            int message,
            @Coerce Object wParam,
            @Coerce Object lParam) {
        try {
            Method method = crosstie$findCallWindowProcMethod(user32.getClass(), false);
            if (method == null) {
                return crosstie$callDefWindowProc(user32, hWnd, message, wParam, lParam);
            }
            return method.invoke(user32, prevWndProc, hWnd, Integer.valueOf(message), wParam, lParam);
        } catch (Throwable t) {
            crosstie$logOnce("CrossTie: IntelliInput CallWindowProc(ptr) failed, using DefWindowProc.", t);
            return crosstie$callDefWindowProc(user32, hWnd, message, wParam, lParam);
        }
    }

    private static Object crosstie$callDefWindowProc(
            Object user32, Object hWnd, int message, Object wParam, Object lParam) {
        try {
            if (user32 == null) {
                return crosstie$makeZeroLResult();
            }
            Method method = crosstie$findDefWindowProc(user32.getClass());
            if (method == null) {
                return crosstie$makeZeroLResult();
            }
            Object result = method.invoke(user32, hWnd, Integer.valueOf(message), wParam, lParam);
            return result != null ? result : crosstie$makeZeroLResult();
        } catch (Throwable t) {
            crosstie$logOnce("CrossTie: IntelliInput DefWindowProc fallback failed.", t);
            return crosstie$makeZeroLResult();
        }
    }

    private static Method crosstie$findCallWindowProcMethod(Class<?> ownerClass, boolean intOverload) {
        Method cached = intOverload ? CALL_WINDOW_PROC_INT_METHOD : CALL_WINDOW_PROC_PTR_METHOD;
        if (cached != null) {
            return cached;
        }
        for (Method method : ownerClass.getMethods()) {
            if (!"CallWindowProc".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 5) {
                continue;
            }
            if (params[2] != int.class) {
                continue;
            }
            if (intOverload != (params[0] == int.class)) {
                continue;
            }
            method.setAccessible(true);
            if (intOverload) {
                CALL_WINDOW_PROC_INT_METHOD = method;
            } else {
                CALL_WINDOW_PROC_PTR_METHOD = method;
            }
            return method;
        }
        return null;
    }

    private static Method crosstie$findDefWindowProc(Class<?> ownerClass) {
        if (DEF_WINDOW_PROC_METHOD != null) {
            return DEF_WINDOW_PROC_METHOD;
        }
        for (Method method : ownerClass.getMethods()) {
            if (!"DefWindowProc".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 4 && params[1] == int.class) {
                method.setAccessible(true);
                DEF_WINDOW_PROC_METHOD = method;
                return method;
            }
        }
        return null;
    }

    private static Object crosstie$makeZeroLResult() {
        try {
            ClassLoader loader = RedirectWindowProcMixin.class.getClassLoader();
            Class<?> lResultClass = Class.forName("com.sun.jna.platform.win32.WinDef$LRESULT", false, loader);
            Constructor<?> ctor = lResultClass.getConstructor(long.class);
            return ctor.newInstance(Long.valueOf(0L));
        } catch (Throwable t) {
            crosstie$logOnce("CrossTie: could not create WinDef$LRESULT(0).", t);
            return null;
        }
    }

    private static void crosstie$logOnce(String message, Throwable t) {
        if (LOGGED_ONCE.compareAndSet(false, true)) {
            CrossTie.LOGGER.warn(message, t);
        }
    }
}
