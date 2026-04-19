package net.suzumiya.crosstie.mixins.ngtlib;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * RTM スクリプト内の GL11 呼び出しを CrossTie のブリッジに書き換える。
 *
 * Angelica 環境で古い行列系 API が直接呼ばれても落ちないように、スクリプト文字列を
 * 実行前に差し替えます。
 */
@Mixin(targets = "jp.ngt.ngtlib.io.ScriptUtil", remap = false)
public abstract class ScriptUtilMixin {
    @Unique
    private static final int REWRITE_CACHE_MAX = 128;
    @Unique
    private static final ConcurrentMap<String, String> REWRITE_CACHE = new ConcurrentHashMap<String, String>();

    @Unique
    private static final String BRIDGE_VAR = "__crosstie_gl11_bridge";
    @Unique
    private static final String BRIDGE_CLASS = "net.suzumiya.crosstie.compat.AngelicaScriptGLBridge";
    @Unique
    private static final String BRIDGE_INIT = "var " + BRIDGE_VAR + " = Java.type(\"" + BRIDGE_CLASS + "\");\n";
    @Unique
    private static final Pattern GL11_METHOD_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_$.])(?:org\\.lwjgl\\.opengl\\.)?GL11\\.(gl[A-Za-z0-9_]+)\\s*\\(");
    @Unique
    private static final Set<String> REWRITABLE_METHODS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "glPushMatrix",
            "glPopMatrix",
            "glTranslatef",
            "glRotatef",
            "glScalef",
            "glTranslated",
            "glRotated",
            "glScaled",
            "glMatrixMode",
            "glLoadIdentity",
            "glBegin",
            "glEnd",
            "glVertex3f",
            "glTexCoord2f",
            "glNormal3f",
            "glColor4f",
            "glEnable",
            "glDisable",
            "glBlendFunc",
            "glAlphaFunc",
            "glDepthMask",
            "glShadeModel")));

    @Redirect(
            method = "doScript(Ljava/lang/String;)Ljavax/script/ScriptEngine;",
            at = @At(value = "INVOKE", target = "Ljavax/script/ScriptEngine;eval(Ljava/lang/String;)Ljava/lang/Object;"),
            remap = false)
    private static Object crosstie$rewriteScriptGL11Calls(ScriptEngine engine, String script) throws ScriptException {
        return engine.eval(crosstie$rewriteGL11MethodCalls(script));
    }

    @Unique
    private static String crosstie$rewriteGL11MethodCalls(String script) {
        if (script == null) {
            return null;
        }

        String cached = REWRITE_CACHE.get(script);
        if (cached != null) {
            return cached;
        }

        if (!script.contains("GL11.gl") && !script.contains("org.lwjgl.opengl.GL11.gl")) {
            crosstie$cacheRewrite(script, script);
            return script;
        }

        Matcher matcher = GL11_METHOD_PATTERN.matcher(script);
        StringBuffer rewritten = new StringBuffer(script.length() + 64);
        boolean changed = false;

        while (matcher.find()) {
            String methodName = matcher.group(1);
            if (REWRITABLE_METHODS.contains(methodName)) {
                String replacement = BRIDGE_VAR + "." + methodName + "(";
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
                changed = true;
            } else {
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(rewritten);

        if (!changed) {
            crosstie$cacheRewrite(script, script);
            return script;
        }

        String result = rewritten.indexOf(BRIDGE_VAR + " = Java.type") >= 0
                ? rewritten.toString()
                : BRIDGE_INIT + rewritten.toString();
        crosstie$cacheRewrite(script, result);
        return result;
    }

    @Unique
    private static void crosstie$cacheRewrite(String key, String value) {
        if (REWRITE_CACHE.size() >= REWRITE_CACHE_MAX) {
            REWRITE_CACHE.clear();
        }
        REWRITE_CACHE.put(key, value);
    }
}
