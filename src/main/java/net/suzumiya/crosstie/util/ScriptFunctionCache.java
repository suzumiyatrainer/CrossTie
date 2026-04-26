package net.suzumiya.crosstie.util;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScriptFunctionCache {

    private static final Map<ScriptEngine, Invocable> INVOCABLE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<ScriptEngine, Invocable>());

    private ScriptFunctionCache() {
    }

    public static Object invoke(ScriptEngine scriptEngine, String function, Object... args) {
        try {
            return getInvocable(scriptEngine).invokeFunction(function, args);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException("Script exec error : " + function, e);
        }
    }

    private static Invocable getInvocable(ScriptEngine scriptEngine) {
        Invocable invocable = INVOCABLE_CACHE.get(scriptEngine);
        if (invocable != null) {
            return invocable;
        }
        invocable = (Invocable) scriptEngine;
        INVOCABLE_CACHE.put(scriptEngine, invocable);
        return invocable;
    }
}

