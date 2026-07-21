package net.suzumiya.crosstie.mixins.kaizpatch;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.Compilable;
import javax.script.CompiledScript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;


@Mixin(targets = "jp.ngt.ngtlib.io.ScriptUtil", remap = false)
public abstract class ScriptUtilInvocableCacheMixin {

    @Unique
    private static Object crosstie$engineFactory;

    @Unique
    private static Object crosstie$getEngineFactory() {
        if (crosstie$engineFactory != null) {
            return crosstie$engineFactory;
        }

        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                ScriptUtilInvocableCacheMixin.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        String[] candidates = {
                "jdk.nashorn.api.scripting.NashornScriptEngineFactory",
                "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory"
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }

            try {
                for (javax.script.ScriptEngineFactory factory : java.util.ServiceLoader
                        .load(javax.script.ScriptEngineFactory.class, loader)) {
                    if (factory == null || "DummyNashorn (Blocked by CrossTie)".equals(factory.getEngineName())) {
                        continue;
                    }
                    String factoryClassName = factory.getClass().getName();
                    for (String candidate : candidates) {
                        if (candidate.equals(factoryClassName)) {
                            crosstie$engineFactory = factory;
                            return crosstie$engineFactory;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // service loader may fail on some classloader setups
            }

            for (String candidate : candidates) {
                try {
                    Class<?> factoryClass = Class.forName(candidate, true, loader);
                    javax.script.ScriptEngineFactory factory = (javax.script.ScriptEngineFactory) factoryClass.getDeclaredConstructor().newInstance();
                    if ("DummyNashorn (Blocked by CrossTie)".equals(factory.getEngineName())) {
                        continue;
                    }
                    crosstie$engineFactory = factory;
                    return crosstie$engineFactory;
                } catch (Throwable ignored) {
                    // try next candidate or loader
                }
            }
        }
        return null;
    }

    @Unique
    private static ScriptEngine crosstie$createScriptEngine() {
        Object factory = crosstie$getEngineFactory();
        if (factory != null) {
            try {
                Method getEngineArgs = factory.getClass().getMethod("getScriptEngine", String[].class);
                return (ScriptEngine) getEngineArgs.invoke(factory,
                        new Object[] { new String[] { "-doe", "--language=es6" } });
            } catch (Throwable ignored) {
                try {
                    Method getEngineNoArgs = factory.getClass().getMethod("getScriptEngine");
                    return (ScriptEngine) getEngineNoArgs.invoke(factory);
                } catch (Throwable ignored2) {
                    // fall through to fallback engine manager
                }
            }
        }

        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                ScriptUtilInvocableCacheMixin.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        String[] names = { "nashorn", "JavaScript", "javascript", "js", "ECMAScript", "ecmascript", "rhino" };
        for (ClassLoader classLoader : loaders) {
            if (classLoader == null) {
                continue;
            }
            try {
                ScriptEngineManager manager = new ScriptEngineManager(classLoader);
                for (String name : names) {
                    ScriptEngine engine = manager.getEngineByName(name);
                    if (engine != null) {
                        return engine;
                    }
                }
            } catch (Throwable ignored) {
                // try next classloader
            }
        }

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            for (String name : names) {
                ScriptEngine engine = manager.getEngineByName(name);
                if (engine != null) {
                    return engine;
                }
            }
        } catch (Throwable ignored) {
            // last attempt failed
        }

        throw new RuntimeException("No JavaScript engine available for script execution.");
    }

    @Unique
    private static final Map<String, CompiledScript> crosstie$compiledScriptCache =
            Collections.synchronizedMap(new LinkedHashMap<String, CompiledScript>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompiledScript> eldest) {
                    return size() > 256;
                }
            });

    /**
     * @author CrossTie
     * @reason KaizPatchX 1.10.0-rc.2 targets jdk.nashorn directly, but some Java 8
     *         runtimes ship without Nashorn. Optimized with cached CompiledScript
     *         to prevent recurrent compilations without sharing stateful ScriptEngines.
     */
    @Overwrite
    public static ScriptEngine doScript(String script) {
        ScriptEngine engine = crosstie$createScriptEngine();
        if (script == null || script.trim().isEmpty()) {
            return engine;
        }

        try {
            String name = engine.getFactory().getEngineName();
            if (name != null && name.toLowerCase().contains("nashorn")) {
                try {
                    engine.eval("load(\"nashorn:mozilla_compat.js\");");
                } catch (ScriptException ignored) {
                }
            }

            CompiledScript compiled = crosstie$compiledScriptCache.get(script);
            if (compiled == null) {
                if (engine instanceof Compilable) {
                    compiled = ((Compilable) engine).compile(script);
                    crosstie$compiledScriptCache.put(script, compiled);
                }
            }

            if (compiled != null) {
                compiled.eval(engine.getContext());
            } else {
                engine.eval(script);
            }
            return engine;
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error\n" + script, e);
        }
    }

    /**
     * @author CrossTie
     * @reason Avoid repeated Invocable casts on the hot RTM/NGT script render path.
     */
    @Overwrite
    public static Object doScriptFunction(ScriptEngine se, String func, Object... args) {
        try {
            return ((Invocable) se).invokeFunction(func, args);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException("Script exec error : " + func, e);
        }
    }
}
