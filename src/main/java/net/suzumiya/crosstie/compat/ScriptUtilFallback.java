package net.suzumiya.crosstie.compat;

import java.lang.reflect.Method;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import net.minecraft.launchwrapper.Launch;

/**
 * Fallback script engine provider for RTM/NGTLib when
 * {@code jdk.nashorn.api.scripting.NashornScriptEngineFactory} is not available
 * on the classpath.
 *
 * <p>
 * This class is invoked by {@code CrossTieClassTransformer} via ASM bytecode
 * replacement of {@code jp.ngt.ngtlib.io.ScriptUtil.doScript(String)}.
 */
public final class ScriptUtilFallback {

    // Flip this switch to log the first script entry and lwjgl3ify detection result.
    private static final boolean DEBUG_MODE = true;
    private static volatile boolean lwjgl3ifyKeyboardCallRedirectLogged;
    private static volatile boolean scriptProbeLogged;
    private static final Pattern LEGACY_KEYBOARD_IS_KEY_DOWN = Pattern.compile(
            "\\bKeyboard\\s*\\.\\s*isKeyDown\\s*\\(");

    private ScriptUtilFallback() {
    }

    /**
     * Replacement for {@code ScriptUtil.doScript(String)}.
     */
    public static ScriptEngine doScript(String script) {
        return doScript(script, "<unnamed script>");
    }

    /**
     * Replacement for {@code ScriptUtil.doScript(String, String)}.
     */
    public static ScriptEngine doScript(String script, String fileName) {
        debugFirstScript(script);
        script = redirectLwjgl3ifyKeyboardCalls(script);
        ScriptEngine engine = createScriptEngine();
        try {
            engine.put(ScriptEngine.FILENAME, fileName);
            String name = engine.getFactory().getEngineName();
            if (name != null && name.toLowerCase().contains("nashorn")) {
                try {
                    engine.eval("load(\"nashorn:mozilla_compat.js\");");
                } catch (ScriptException ignored) {
                    // compatibility helper not available, continue without it
                }
            }
            engine.eval(script);
            return engine;
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error\n" + script, e);
        }
    }

    /**
     * Routes legacy LWJGL2 Keyboard package references through CrossTie when
     * lwjgl3ify is installed. This works even when the LWJGL Keyboard facade
     * was loaded before LaunchWrapper's transformers were registered.
     */
    private static String redirectLwjgl3ifyKeyboardCalls(String script) {
        if (script == null || !LEGACY_KEYBOARD_IS_KEY_DOWN.matcher(script).find()) {
            return script;
        }

        if (!isLwjgl3ifyPresent()) {
            return script;
        }

        String redirected = LEGACY_KEYBOARD_IS_KEY_DOWN.matcher(script).replaceAll(
                "Packages.net.suzumiya.crosstie.compat.lwjgl3ify.KeyboardAdapter.isKeyDown(");
        if (!lwjgl3ifyKeyboardCallRedirectLogged) {
            synchronized (ScriptUtilFallback.class) {
                if (!lwjgl3ifyKeyboardCallRedirectLogged) {
                    System.out.println("[CrossTie KeyDebug] Redirected Keyboard.isKeyDown script calls to CrossTie adapter");
                    lwjgl3ifyKeyboardCallRedirectLogged = true;
                }
            }
        }
        return redirected;
    }

    private static boolean isLwjgl3ifyPresent() {
        ClassLoader[] loaders = {
                Launch.classLoader,
                Thread.currentThread().getContextClassLoader(),
                ScriptUtilFallback.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class.forName("org.lwjglx.input.Keyboard", false, loader);
                return true;
            } catch (ClassNotFoundException ignored) {
                // Try the next loader.
            } catch (LinkageError ignored) {
                // A partially installed lwjgl3ify should not break unrelated scripts.
            }
        }
        return false;
    }

    private static void debugFirstScript(String script) {
        if (!DEBUG_MODE || scriptProbeLogged) {
            return;
        }
        synchronized (ScriptUtilFallback.class) {
            if (scriptProbeLogged) {
                return;
            }
            scriptProbeLogged = true;
        }

        boolean hasLegacyCall = script != null && LEGACY_KEYBOARD_IS_KEY_DOWN.matcher(script).find();
        StringBuilder loaders = new StringBuilder();
        ClassLoader[] candidates = {
                Launch.classLoader,
                Thread.currentThread().getContextClassLoader(),
                ScriptUtilFallback.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : candidates) {
            if (loaders.length() > 0) {
                loaders.append(", ");
            }
            loaders.append(loader == null ? "null" : loader.getClass().getName());
        }
        System.out.println("[CrossTie KeyDebug] ScriptUtilFallback entered; scriptLength="
                + (script == null ? -1 : script.length()) + " legacyKeyboardCall=" + hasLegacyCall
                + " loaders=[" + loaders + "] lwjgl3ifyKeyboard=" + isLwjgl3ifyPresent());
    }

    /**
     * Creates a JavaScript engine by probing available classloaders and engine
     * factories. Tries Nashorn first, then falls back to any available engine.
     */
    private static ScriptEngine createScriptEngine() {
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                ScriptUtilFallback.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        // 1. Try to find NashornScriptEngineFactory via class name
        String[] candidates = {
                "jdk.nashorn.api.scripting.NashornScriptEngineFactory",
                "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory"
        };

        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }

            // Try via ServiceLoader
            try {
                for (javax.script.ScriptEngineFactory factory : java.util.ServiceLoader
                        .load(javax.script.ScriptEngineFactory.class, loader)) {
                    if (factory == null) {
                        continue;
                    }
                    String factoryClassName = factory.getClass().getName();
                    for (String candidate : candidates) {
                        if (candidate.equals(factoryClassName)) {
                            try {
                                Method getEngine = factory.getClass()
                                        .getMethod("getScriptEngine", String[].class);
                                return (ScriptEngine) getEngine.invoke(factory,
                                        (Object) new String[] { "-doe", "--language=es6" });
                            } catch (Throwable ignored) {
                                try {
                                    Method getEngine = factory.getClass()
                                            .getMethod("getScriptEngine");
                                    return (ScriptEngine) getEngine.invoke(factory);
                                } catch (Throwable ignored2) {
                                    // fall through
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // Try via Class.forName
            for (String candidate : candidates) {
                try {
                    Class<?> factoryClass = Class.forName(candidate, true, loader);
                    Object factory = factoryClass.getDeclaredConstructor().newInstance();
                    Method getEngine = factoryClass.getMethod("getScriptEngine", String[].class);
                    return (ScriptEngine) getEngine.invoke(factory,
                            (Object) new String[] { "-doe", "--language=es6" });
                } catch (Throwable ignored) {
                }
            }
        }

        // 2. Fallback: ScriptEngineManager
        String[] names = { "nashorn", "JavaScript", "javascript", "js", "ECMAScript",
                "ecmascript", "rhino" };
        for (ClassLoader classLoader : loaders) {
            if (classLoader == null) {
                continue;
            }
            try {
                ScriptEngineManager manager = new ScriptEngineManager(classLoader);
                for (String n : names) {
                    ScriptEngine engine = manager.getEngineByName(n);
                    if (engine != null) {
                        return engine;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            for (String n : names) {
                ScriptEngine engine = manager.getEngineByName(n);
                if (engine != null) {
                    return engine;
                }
            }
        } catch (Throwable ignored) {
        }

        throw new RuntimeException("No JavaScript engine available for script execution.");
    }
}
