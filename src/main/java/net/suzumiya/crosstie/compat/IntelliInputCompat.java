package net.suzumiya.crosstie.compat;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

/**
 * IntelliInput の {@code EnumWindows} コールバック用の安全な {@code GetWindowText}。
 *
 * <p>
 * IntelliInput は全プロセスのウィンドウに対して {@code GetWindowText} を呼ぶが、
 * 応答しない他プロセスのウィンドウ(lwjgl3ify の親プロセス、IDE 等)では
 * WM_GETTEXT が戻らず、クライアントスレッドが起動時に永久にブロックする。
 * 自プロセス以外のウィンドウは問い合わせずに空文字扱い(0)を返す。
 * JNA は IntelliInput 側に同梱されているため、リフレクションで呼び出す。
 */
public final class IntelliInputCompat {

    private static final int OWN_PID = resolvePid();

    private IntelliInputCompat() {
    }

    private static int resolvePid() {
        try {
            String n = ManagementFactory.getRuntimeMXBean().getName();
            return Integer.parseInt(n.substring(0, n.indexOf('@')));
        } catch (Throwable t) {
            return -1;
        }
    }

    private static java.util.List<Method> allMethods(Object target) {
        java.util.List<Method> list = new java.util.ArrayList<>();
        for (Class<?> i : target.getClass().getInterfaces()) {
            list.addAll(java.util.Arrays.asList(i.getMethods()));
        }
        return list;
    }

    private static Method find(Object target, String name, int argc) {
        for (Method m : allMethods(target)) {
            if (m.getName().equals(name) && m.getParameterTypes().length == argc) {
                return m;
            }
        }
        return null;
    }

    /** {@code user32.GetWindowText(hwnd, buffer, max)} の置き換え。 */
    public static int safeGetWindowText(Object user32, Object hwnd, char[] buffer, int max) {
        try {
            if (OWN_PID > 0) {
                Method getPid = find(user32, "GetWindowThreadProcessId", 2);
                if (getPid != null) {
                    Class<?> refClass = getPid.getParameterTypes()[1];
                    Object ref = refClass.getConstructor().newInstance();
                    getPid.invoke(user32, hwnd, ref);
                    int pid = (Integer) refClass.getMethod("getValue").invoke(ref);
                    if (pid != OWN_PID) {
                        return 0;
                    }
                }
            }
            Method getText = null;
            for (Method m : allMethods(user32)) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("GetWindowText") && p.length == 3 && p[1] == char[].class) {
                    getText = m;
                    break;
                }
            }
            if (getText != null) {
                return (Integer) getText.invoke(user32, hwnd, buffer, max);
            }
        } catch (Throwable ignored) {
            // 失敗時は空文字扱い
        }
        return 0;
    }
}
