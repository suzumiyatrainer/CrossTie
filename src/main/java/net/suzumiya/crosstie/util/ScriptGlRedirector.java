package net.suzumiya.crosstie.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptGlRedirector {

    private static final int MAX_SCRIPT_CACHE_SIZE = 512;
    private static final String GLSM_IMPORT =
            "importPackage(Packages.com.gtnewhorizons.angelica.glsm);\n";
    private static final Pattern QUALIFIED_GL_METHOD =
            Pattern.compile("\\b(?:Packages\\.)?org\\.lwjgl\\.opengl\\.(GL\\w+)\\.(\\w+)\\b");

    private static final Map<String, String> TRANSFORMED_SCRIPTS = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SCRIPT_CACHE_SIZE;
                }
            });

    private static Method getMethodRedirectPattern;
    private static Method getTargetMethodName;

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

        Method targetMethod = getTargetMethodName();
        if (targetMethod == null) {
            return script;
        }

        TransformResult qualifiedNames = redirectQualifiedGlCalls(script, targetMethod);
        TransformResult shortNames = redirectShortGlCalls(qualifiedNames.script, targetMethod);
        if (!shortNames.changed && !qualifiedNames.changed) {
            return script;
        }

        return hasGlsmImport(shortNames.script) ? shortNames.script : GLSM_IMPORT + shortNames.script;
    }

    private static TransformResult redirectShortGlCalls(String script, Method targetMethod) {
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
                String targetMethodName = getTargetMethodName(targetMethod, matcher.group());
                if (targetMethodName == null) {
                    continue;
                }

                matcher.appendReplacement(result, Matcher.quoteReplacement("GLStateManager." + targetMethodName));
                changed = true;
            }
            matcher.appendTail(result);
            return new TransformResult(changed ? result.toString() : script, changed);
        } catch (ReflectiveOperationException e) {
            return new TransformResult(script, false);
        }
    }

    private static TransformResult redirectQualifiedGlCalls(String script, Method targetMethod) {
        if (script.indexOf("org.lwjgl.opengl.") < 0) {
            return new TransformResult(script, false);
        }

        Matcher matcher = QUALIFIED_GL_METHOD.matcher(script);
        StringBuffer result = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String classAndMethod = matcher.group(1) + "." + matcher.group(2);
            String targetMethodName = getTargetMethodName(targetMethod, classAndMethod);
            if (targetMethodName == null) {
                continue;
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement("GLStateManager." + targetMethodName));
            changed = true;
        }
        matcher.appendTail(result);
        return new TransformResult(changed ? result.toString() : script, changed);
    }

    private static Method getMethodRedirectPattern() {
        if (getMethodRedirectPattern != null) {
            return getMethodRedirectPattern;
        }

        Class<?> redirects = getGlRedirectsClass();
        if (redirects == null) {
            return null;
        }

        try {
            getMethodRedirectPattern = redirects.getMethod("getMethodRedirectPattern");
            return getMethodRedirectPattern;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method getTargetMethodName() {
        if (getTargetMethodName != null) {
            return getTargetMethodName;
        }

        Class<?> redirects = getGlRedirectsClass();
        if (redirects == null) {
            return null;
        }

        try {
            getTargetMethodName = redirects.getMethod("getTargetMethodName", String.class);
            return getTargetMethodName;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Class<?> getGlRedirectsClass() {
        try {
            return Class.forName("com.gtnewhorizons.angelica.api.GLRedirects");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static String getTargetMethodName(Method targetMethod, String classAndMethod) {
        try {
            Object target = targetMethod.invoke(null, classAndMethod);
            return target instanceof String ? (String) target : null;
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
