package net.suzumiya.crosstie.mixins.kaizpatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
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
    private static final ThreadLocal<ScriptEngine> crosstie$threadLocalEngine = new ThreadLocal<ScriptEngine>() {
        @Override
        protected ScriptEngine initialValue() {
            return crosstie$createScriptEngine();
        }
    };

    /**
     * @author CrossTie
     * @reason Optimized script execution using ThreadLocal and ScriptContext Proxy
     *         to prevent concurrent execution crashes, variables leakage, and ClassLoader overheads
     *         while avoiding Nashorn's CompiledScript ReferenceError bugs.
     */
    @Overwrite
    public static ScriptEngine doScript(String script) {
        ScriptEngine realEngine = crosstie$threadLocalEngine.get();
        if (script == null || script.trim().isEmpty()) {
            return realEngine;
        }

        ScriptContext context = new SimpleScriptContext();
        Bindings bindings = realEngine.createBindings();
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        ScriptEngine proxyEngine = crosstie$createEngineProxy(realEngine, context);

        try {
            String name = realEngine.getFactory().getEngineName();
            if (name != null && name.toLowerCase().contains("nashorn")) {
                try {
                    proxyEngine.eval("load(\"nashorn:mozilla_compat.js\");");
                } catch (ScriptException ignored) {
                }
            }
            proxyEngine.eval(script);
            return proxyEngine;
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error\n" + script, e);
        }
    }

    @Unique
    private static ScriptEngine crosstie$createEngineProxy(final ScriptEngine realEngine, final ScriptContext context) {
        Class<?>[] interfaces = new Class<?>[] { ScriptEngine.class, Invocable.class };
        return (ScriptEngine) Proxy.newProxyInstance(
                ScriptUtilInvocableCacheMixin.class.getClassLoader(),
                interfaces,
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();

                        if (method.getDeclaringClass() == Invocable.class) {
                            if ("invokeFunction".equals(name)) {
                                String funcName = (String) args[0];
                                Object[] funcArgs = (Object[]) args[1];
                                Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
                                Object funcObj = bindings.get(funcName);
                                if (funcObj == null) {
                                    throw new NoSuchMethodException("No such function: " + funcName);
                                }
                                synchronized (realEngine) {
                                    ScriptContext oldContext = realEngine.getContext();
                                    try {
                                        realEngine.setContext(context);
                                        return ((Invocable) realEngine).invokeFunction(funcName, funcArgs);
                                    } finally {
                                        realEngine.setContext(oldContext);
                                    }
                                }
                            }

                            if ("getInterface".equals(name)) {
                                throw new UnsupportedOperationException("getInterface is not supported on proxy");
                            }
                        }

                        if ("eval".equals(name)) {
                            if (args.length == 1 && args[0] instanceof String) {
                                synchronized (realEngine) {
                                    ScriptContext oldContext = realEngine.getContext();
                                    try {
                                        realEngine.setContext(context);
                                        return realEngine.eval((String) args[0]);
                                    } finally {
                                        realEngine.setContext(oldContext);
                                    }
                                }
                            }
                        }

                        if ("get".equals(name)) {
                            return context.getBindings(ScriptContext.ENGINE_SCOPE).get(args[0]);
                        }

                        if ("put".equals(name)) {
                            context.getBindings(ScriptContext.ENGINE_SCOPE).put((String) args[0], args[1]);
                            return null;
                        }

                        if ("getBindings".equals(name)) {
                            return context.getBindings(((Integer) args[0]).intValue());
                        }

                        if ("setBindings".equals(name)) {
                            context.setBindings((Bindings) args[0], ((Integer) args[1]).intValue());
                            return null;
                        }

                        if ("getContext".equals(name)) {
                            return context;
                        }

                        if ("setContext".equals(name)) {
                            throw new UnsupportedOperationException("setContext is not supported on proxy");
                        }

                        try {
                            return method.invoke(realEngine, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                }
        );
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
