package net.suzumiya.crosstie.utils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptGlRedirector {

    private static final int MAX_SCRIPT_CACHE_SIZE = 512;
    private static final String GLSM_IMPORT = "importPackage(Packages.com.gtnewhorizons.angelica.glsm);\n";
    private static final Pattern QUALIFIED_GL_METHOD = Pattern
            .compile("\\b(?:Packages\\.)?org\\.lwjgl\\.opengl\\.(GL\\w+)\\.(\\w+)\\b");

    private static final Map<String, String> TRANSFORMED_SCRIPTS = Collections
            .synchronizedMap(new LinkedHashMap<String, String>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SCRIPT_CACHE_SIZE;
                }
            });

    private static Method getMethodRedirectPattern;
    private static Method getTargetMethodName;
    private static Method getTargetReference;
    private static Method redirectScriptMethod;
    private static boolean modernMethodsChecked;

    private ScriptGlRedirector() {
    }

    public static String transform(String script) {
        if (script == null) {
            return null;
        }

        String cached = TRANSFORMED_SCRIPTS.get(script);
        if (cached != null) {
            return cached;
        }

        String transformed = transformUncached(script);
        TRANSFORMED_SCRIPTS.put(script, transformed);
        return transformed;
    }

    private static String transformUncached(String script) {
        if (script.indexOf("GL") < 0 && script.indexOf("org.lwjgl.opengl.") < 0) {
            return script;
        }

        ensureMethodsInitialized();
        if (getTargetReference == null && getTargetMethodName == null && redirectScriptMethod == null) {
            return script;
        }

        TransformResult qualifiedNames = redirectQualifiedGlCalls(script);

        if (redirectScriptMethod != null) {
            String delegated = invokeRedirectScript(qualifiedNames.script);
            if (delegated != null) {
                if (!qualifiedNames.changed && delegated.equals(script)) {
                    return script;
                }
                return hasGlsmImport(delegated) ? delegated : GLSM_IMPORT + delegated;
            }
        }

        TransformResult shortNames = redirectShortGlCalls(qualifiedNames.script);
        if (!shortNames.changed && !qualifiedNames.changed) {
            return script;
        }

        return hasGlsmImport(shortNames.script) ? shortNames.script : GLSM_IMPORT + shortNames.script;
    }

    private static TransformResult redirectShortGlCalls(String script) {
        Method patternMethod = getMethodRedirectPattern();
        if (patternMethod == null) {
            return new TransformResult(script, false);
        }

        try {
            Object patternObject = patternMethod.invoke(null);
            if (!(patternObject instanceof Pattern)) {
                return new TransformResult(script, false);
            }

            Matcher matcher = ((Pattern) patternObject).matcher(script);
            StringBuffer result = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                String targetReference = resolveTargetReference(matcher.group());
                if (targetReference == null) {
                    continue;
                }

                matcher.appendReplacement(result, Matcher.quoteReplacement(targetReference));
                changed = true;
            }
            matcher.appendTail(result);
            return new TransformResult(changed ? result.toString() : script, changed);
        } catch (ReflectiveOperationException e) {
            return new TransformResult(script, false);
        }
    }

    private static TransformResult redirectQualifiedGlCalls(String script) {
        if (script.indexOf("org.lwjgl.opengl.") < 0) {
            return new TransformResult(script, false);
        }

        Matcher matcher = QUALIFIED_GL_METHOD.matcher(script);
        StringBuffer result = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String classAndMethod = matcher.group(1) + "." + matcher.group(2);
            String targetReference = resolveTargetReference(classAndMethod);
            if (targetReference == null) {
                continue;
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(targetReference));
            changed = true;
        }
        matcher.appendTail(result);
        return new TransformResult(changed ? result.toString() : script, changed);
    }

    private static synchronized void ensureMethodsInitialized() {
        if (modernMethodsChecked) {
            return;
        }
        modernMethodsChecked = true;

        Class<?> redirects = getGlRedirectsClass();
        if (redirects == null) {
            return;
        }

        try {
            getMethodRedirectPattern = redirects.getMethod("getMethodRedirectPattern");
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            getTargetMethodName = redirects.getMethod("getTargetMethodName", String.class);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            getTargetReference = redirects.getMethod("getTargetReference", String.class);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            redirectScriptMethod = redirects.getMethod("redirectScript", String.class);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method getMethodRedirectPattern() {
        ensureMethodsInitialized();
        return getMethodRedirectPattern;
    }

    private static Class<?> getGlRedirectsClass() {
        try {
            return Class.forName("com.gtnewhorizons.angelica.api.GLRedirects");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static String resolveTargetReference(String classAndMethod) {
        ensureMethodsInitialized();
        if (getTargetReference != null) {
            try {
                Object target = getTargetReference.invoke(null, classAndMethod);
                if (target instanceof String) {
                    return (String) target;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        if (getTargetMethodName != null) {
            try {
                Object target = getTargetMethodName.invoke(null, classAndMethod);
                if (target instanceof String) {
                    return "GLStateManager." + (String) target;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static String invokeRedirectScript(String script) {
        if (redirectScriptMethod == null) {
            return null;
        }
        try {
            Object transformed = redirectScriptMethod.invoke(null, script);
            return transformed instanceof String ? (String) transformed : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean hasGlsmImport(String script) {
        return script.indexOf("Packages.com.gtnewhorizons.angelica.glsm") >= 0;
    }

    private static final class TransformResult {
        private final String script;
        private final boolean changed;

        private TransformResult(String script, boolean changed) {
            this.script = script;
            this.changed = changed;
        }
    }
}
