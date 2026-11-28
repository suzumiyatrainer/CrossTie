package net.suzumiya.crosstie.mixins.kaizpatch;

import java.lang.reflect.Method;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import net.suzumiya.crosstie.utils.CrossTieScriptEngineWrapper;
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

        ClassLoader[] loaders = { Thread.currentThread().getContextClassLoader(),
                ScriptUtilInvocableCacheMixin.class.getClassLoader(), ClassLoader.getSystemClassLoader() };

        String[] candidates = { "jdk.nashorn.api.scripting.NashornScriptEngineFactory",
                "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory" };
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
                    javax.script.ScriptEngineFactory factory = (javax.script.ScriptEngineFactory) factoryClass
                            .getDeclaredConstructor().newInstance();
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

        ClassLoader[] loaders = { Thread.currentThread().getContextClassLoader(),
                ScriptUtilInvocableCacheMixin.class.getClassLoader(), ClassLoader.getSystemClassLoader() };

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
    private static final ThreadLocal<ScriptEngine> crosstie$threadLocalEngine = new ThreadLocal<ScriptEngine>() {
        @Override
        protected ScriptEngine initialValue() {
            return crosstie$createScriptEngine();
        }
    };

    /**
     * @author CrossTie
     * @reason Optimized script execution using ThreadLocal and concrete wrapper
     *         (eliminating dynamic Proxy overhead) while preventing concurrent crashes.
     */
    @Overwrite
    public static ScriptEngine doScript(String script) {
        return crosstie$doScriptInternal(script, null);
    }

    /**
     * @author CrossTie
     * @reason KaizPatchX 2-arg overload (used by PartsRenderer etc.) also benefits from concrete wrapper.
     */
    @Overwrite
    public static ScriptEngine doScript(String script, String fileName) {
        return crosstie$doScriptInternal(script, fileName);
    }

    @Unique
    private static ScriptEngine crosstie$doScriptInternal(String script, String fileName) {
        ScriptEngine realEngine = crosstie$threadLocalEngine.get();
        if (script == null || script.trim().isEmpty()) {
            return realEngine;
        }

        script = net.suzumiya.crosstie.compat.ScriptUtilFallback.redirectLwjgl3ifyKeyboardCalls(script);

        ScriptContext context = new SimpleScriptContext();
        Bindings bindings = realEngine.createBindings();
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        CrossTieScriptEngineWrapper wrapper = new CrossTieScriptEngineWrapper(realEngine, context);
        if (fileName != null && !fileName.isEmpty()) {
            wrapper.put(ScriptEngine.FILENAME, fileName);
        }

        try {
            String name = realEngine.getFactory().getEngineName();
            if (name != null && name.toLowerCase().contains("nashorn")) {
                try {
                    wrapper.eval("load(\"nashorn:mozilla_compat.js\");");
                } catch (ScriptException ignored) {
                }
            }
            wrapper.eval(script);
            return wrapper;
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error\n" + script, e);
        }
    }

    /**
     * @author CrossTie
     * @reason Monomorphic fast path for CrossTieScriptEngineWrapper + safe Invocable dispatch.
     */
    @Overwrite
    public static Object doScriptFunction(ScriptEngine se, String func, Object... args) {
        try {
            if (se instanceof CrossTieScriptEngineWrapper) {
                return ((CrossTieScriptEngineWrapper) se).invokeFunction(func, args);
            }
            return ((Invocable) se).invokeFunction(func, args);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ScriptException e) {
            // 描画スクリプト内の null 参照 (例: SuperRailBuilder3 の getAroundRailData で
            // レール未ロード時に TileEntity が null) によるクラッシュを、そのフレームの描画スキップで回避する
            String msg = e.getMessage();
            if ("render".equals(func) && msg != null && msg.contains("TypeError")
                    && (msg.contains("of null") || msg.contains("of undefined")
                            || msg.contains("from null") || msg.contains("from undefined"))) {
                if (!crosstie$renderErrorLogged) {
                    crosstie$renderErrorLogged = true;
                    System.err.println("[CrossTie] Suppressed render script null error (further ones hidden): " + msg);
                }
                return null;
            }
            throw new RuntimeException("Script exec error : " + func, e);
        }
    }

    @Unique
    private static volatile boolean crosstie$renderErrorLogged;

    /**
     * @author CrossTie
     * @reason Fast path for doScriptIgnoreError without creating Exception stack traces for missing functions.
     */
    @Overwrite
    public static Object doScriptIgnoreError(ScriptEngine se, String func, Object... args) {
        try {
            return doScriptFunction(se, func, args);
        } catch (Throwable e) {
            return null;
        }
    }
}
