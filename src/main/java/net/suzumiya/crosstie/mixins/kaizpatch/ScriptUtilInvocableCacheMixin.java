package net.suzumiya.crosstie.mixins.kaizpatch;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "jp.ngt.ngtlib.io.ScriptUtil", remap = false)
public abstract class ScriptUtilInvocableCacheMixin {

    @Unique
    private static final Map<ScriptEngine, WeakReference<Invocable>> crosstie$invocables =
            Collections.synchronizedMap(new WeakHashMap<ScriptEngine, WeakReference<Invocable>>());

    /**
     * @author CrossTie
     * @reason Avoid repeated Invocable casts on the hot RTM/NGT script render path.
     */
    @Overwrite
    public static Object doScriptFunction(ScriptEngine se, String func, Object... args) {
        try {
            return crosstie$getInvocable(se).invokeFunction(func, args);
        } catch (NoSuchMethodException | ScriptException e) {
            throw new RuntimeException("Script exec error : " + func, e);
        }
    }

    @Unique
    private static Invocable crosstie$getInvocable(ScriptEngine se) {
        WeakReference<Invocable> reference = crosstie$invocables.get(se);
        Invocable invocable = reference != null ? reference.get() : null;
        if (invocable == null) {
            invocable = (Invocable) se;
            crosstie$invocables.put(se, new WeakReference<>(invocable));
        }
        return invocable;
    }
}
