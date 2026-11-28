package net.suzumiya.crosstie.utils;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3-1: {@code java.lang.reflect.Proxy} を廃止した具象 {@link ScriptEngine} / {@link Invocable} ラッパー。
 *
 * <ul>
 *   <li>動的 Proxy のディスパッチ・引数配列化・メソッド名文字列比較を完全に排除し、JIT インライン化を可能にする。</li>
 *   <li>未定義関数名をスレッドセーフな負キャッシュ ({@code missingFunctions}) に保持し、
 *       {@code eval}/{@code put}/{@code setBindings} 時に確実に無効化する。</li>
 * </ul>
 */
public final class CrossTieScriptEngineWrapper implements ScriptEngine, Invocable {

    private final ScriptEngine realEngine;
    private final Invocable realInvocable;
    private final ScriptContext context;
    private final Set<String> missingFunctions =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public CrossTieScriptEngineWrapper(ScriptEngine realEngine, ScriptContext context) {
        this.realEngine = realEngine;
        this.realInvocable = (realEngine instanceof Invocable) ? (Invocable) realEngine : null;
        this.context = context;
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
        if (name == null || this.missingFunctions.contains(name)) {
            return null;
        }
        Bindings bindings = this.context.getBindings(ScriptContext.ENGINE_SCOPE);
        if (bindings == null || bindings.get(name) == null) {
            this.missingFunctions.add(name);
            return null;
        }
        if (this.realInvocable == null) {
            throw new UnsupportedOperationException("Underlying ScriptEngine is not Invocable");
        }
        synchronized (this.realEngine) {
            ScriptContext oldContext = this.realEngine.getContext();
            try {
                this.realEngine.setContext(this.context);
                return this.realInvocable.invokeFunction(name, args);
            } finally {
                this.realEngine.setContext(oldContext);
            }
        }
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
        if (this.realInvocable == null) {
            throw new UnsupportedOperationException("Underlying ScriptEngine is not Invocable");
        }
        synchronized (this.realEngine) {
            ScriptContext oldContext = this.realEngine.getContext();
            try {
                this.realEngine.setContext(this.context);
                return this.realInvocable.invokeMethod(thiz, name, args);
            } finally {
                this.realEngine.setContext(oldContext);
            }
        }
    }

    @Override
    public <T> T getInterface(Class<T> clasz) {
        throw new UnsupportedOperationException("getInterface is not supported on CrossTieScriptEngineWrapper");
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz) {
        throw new UnsupportedOperationException("getInterface is not supported on CrossTieScriptEngineWrapper");
    }

    @Override
    public Object eval(String script) throws ScriptException {
        this.missingFunctions.clear();
        synchronized (this.realEngine) {
            ScriptContext oldContext = this.realEngine.getContext();
            try {
                this.realEngine.setContext(this.context);
                return this.realEngine.eval(script);
            } finally {
                this.realEngine.setContext(oldContext);
            }
        }
    }

    @Override
    public Object eval(Reader reader) throws ScriptException {
        this.missingFunctions.clear();
        synchronized (this.realEngine) {
            ScriptContext oldContext = this.realEngine.getContext();
            try {
                this.realEngine.setContext(this.context);
                return this.realEngine.eval(reader);
            } finally {
                this.realEngine.setContext(oldContext);
            }
        }
    }

    @Override
    public Object eval(String script, ScriptContext ctx) throws ScriptException {
        this.missingFunctions.clear();
        synchronized (this.realEngine) {
            return this.realEngine.eval(script, ctx);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext ctx) throws ScriptException {
        this.missingFunctions.clear();
        synchronized (this.realEngine) {
            return this.realEngine.eval(reader, ctx);
        }
    }

    @Override
    public Object eval(String script, Bindings n) throws ScriptException {
        this.missingFunctions.clear();
        synchronized (this.realEngine) {
            return this.realEngine.eval(script, n);
        }
    }

    @Override
    public Object eval(Reader reader, Bindings n) throws ScriptException {
        this.missingFunctions.clear();
        synchronized (this.realEngine) {
            return this.realEngine.eval(reader, n);
        }
    }

    @Override
    public void put(String key, Object value) {
        if (key != null) {
            this.missingFunctions.remove(key);
        }
        this.context.getBindings(ScriptContext.ENGINE_SCOPE).put(key, value);
    }

    @Override
    public Object get(String key) {
        return this.context.getBindings(ScriptContext.ENGINE_SCOPE).get(key);
    }

    @Override
    public Bindings getBindings(int scope) {
        return this.context.getBindings(scope);
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {
        this.missingFunctions.clear();
        this.context.setBindings(bindings, scope);
    }

    @Override
    public Bindings createBindings() {
        return this.realEngine.createBindings();
    }

    @Override
    public ScriptContext getContext() {
        return this.context;
    }

    @Override
    public void setContext(ScriptContext context) {
        throw new UnsupportedOperationException("setContext is not supported on CrossTieScriptEngineWrapper");
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return this.realEngine.getFactory();
    }
}
