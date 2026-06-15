package net.suzumiya.crosstie.compat;

import java.lang.reflect.Method;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

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

    private ScriptUtilFallback() {
    }

    /**
     * Replacement for {@code ScriptUtil.doScript(String)}.
     */
    public static ScriptEngine doScript(String script) {
        ScriptEngine engine = createScriptEngine();
        try {
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