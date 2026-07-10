package net.suzumiya.crosstie.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized mod detection utility that scans mods directories for JAR/ZIP
 * files.
 *
 * <p>
 * At the coremod stage (IFMLLoadingPlugin), mod classes are NOT on the
 * classpath yet, so we cannot use Class.forName(). This detector scans the mods
 * folder(s) for known mod JAR files by name pattern matching.
 *
 * <p>
 * Patterns are lower-cased for case-insensitive matching.
 */
public class ModDetector {

    private static final Map<String, String[]> MOD_PATTERNS = new HashMap<>();

    static {
        // ATSAssistMod
        MOD_PATTERNS.put("ATSAssistMod", new String[] { "atsassist" });

        // Performance mods (Angelica ecosystem)
        MOD_PATTERNS.put("Angelica", new String[] { "angelica" });
        MOD_PATTERNS.put("AngelicaGlsm", new String[] { "angelica" });
        MOD_PATTERNS.put("ArchaicFix", new String[] { "archaicfix" });
        MOD_PATTERNS.put("CoreTweaks", new String[] { "coretweaks" });
        MOD_PATTERNS.put("GTNHLib", new String[] { "gtnhlib" });
        MOD_PATTERNS.put("Hodgepodge", new String[] { "hodgepodge" });
        MOD_PATTERNS.put("UniMixins", new String[] { "unimixins" });

        // RTM ecosystem
        MOD_PATTERNS.put("RTM", new String[] { "rtm", "realtrainmod", "kaizpatch" });
        MOD_PATTERNS.put("NGTLib", new String[] { "ngtlib", "kaizpatch" });
        MOD_PATTERNS.put("MCTE", new String[] { "mcte", "mcterraineditor", "kaizpatch" });
        MOD_PATTERNS.put("NGTScriptUtil", new String[] { "ngtlib", "kaizpatch" });
        MOD_PATTERNS.put("KaizPatch", new String[] { "kaizpatch" });
        MOD_PATTERNS.put("RailMapCustom", new String[] { "rtm", "kaizpatch" });

        // ProjectRed
        MOD_PATTERNS.put("ProjectRed", new String[] { "projectred" });

        // Incompatible mods
        MOD_PATTERNS.put("MinFo", new String[] { "minfo" });

        // OptiFine / FastCraft (brightness fix targets)
        MOD_PATTERNS.put("OptiFine", new String[] { "optifine" });
        MOD_PATTERNS.put("FastCraft", new String[] { "fastcraft" });

        // LiteLoader ecosystem (Macro / Keybind Mod ships as .litemod)
        MOD_PATTERNS.put("LiteLoader", new String[] { "liteloader", "macro", "keybind" });
        MOD_PATTERNS.put("MacroMod", new String[] { "macro", "keybind" });

        // CustomNPC+
        MOD_PATTERNS.put("CustomNpc", new String[] { "customnpc" });
    }

    private final File mcDataDir;
    private final Map<String, Boolean> cache = new HashMap<>();

    public ModDetector(File mcDataDir) {
        this.mcDataDir = mcDataDir;
    }

    /**
     * Check if a named mod is detected by scanning mods directories.
     */
    public boolean isModPresent(String modName) {
        return cache.computeIfAbsent(modName, this::detectMod);
    }

    private boolean detectMod(String modName) {
        String[] patterns = MOD_PATTERNS.get(modName);
        if (patterns == null) {
            return false;
        }
        return scanPatterns(patterns);
    }

    private boolean scanPatterns(String[] patterns) {
        // The original implementation returned false immediately when mcDataDir was
        // null,
        // which prevented detection when the FML data map does not provide the
        // directory.
        // We now continue to collect possible mod directories, relying on the jar
        // location
        // fallback (jarModsDir) that works for coremods loaded from the mods folder.

        // Collect all mod directory candidates (may include null entries which are
        // ignored later)
        File[] modDirs = collectModDirectories();

        for (File dir : modDirs) {
            if (dir == null || !dir.isDirectory())
                continue;

            if (scanDirectory(dir.toPath(), patterns)) {
                return true;
            }

            // Check subdirectories (e.g. mods/1.7.10/)
            try (DirectoryStream<Path> subDirs = Files.newDirectoryStream(dir.toPath())) {
                for (Path sub : subDirs) {
                    if (Files.isDirectory(sub) && scanDirectory(sub, patterns)) {
                        return true;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        return false;
    }

    /**
     * Scan a single directory for JAR/ZIP files matching any of the given patterns.
     */
    private boolean scanDirectory(Path dirPath, String[] patterns) {
        // Scan direct JAR/ZIP files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.{jar,zip}")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString().toLowerCase();
                for (String pattern : patterns) {
                    if (fileName.contains(pattern.toLowerCase())) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
        }

        // Also check for .litemod files (LiteLoader mods)
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.litemod")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString().toLowerCase();
                for (String pattern : patterns) {
                    if (fileName.contains(pattern.toLowerCase())) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return false;
    }

    /**
     * Collect possible mod directories from various launcher layouts.
     */
    private File[] collectModDirectories() {
        // The original implementation assumed mcDataDir was always non‑null, which
        // caused
        // a NullPointerException when it was null. We now guard each directory
        // creation.
        File modsDir = null;
        File modsVersionDir = null;
        File parentModsDir = null;

        if (mcDataDir != null) {
            modsDir = new File(mcDataDir, "mods");
            modsVersionDir = new File(mcDataDir, "mods/1.7.10");

            // For Modrinth/Prism: mcDataDir is the profile directory
            // For vanilla/forge: mcDataDir is the .minecraft/ run directory
            // Try parent variations too
            parentModsDir = mcDataDir.getParentFile() != null
                    ? new File(mcDataDir.getParentFile(), mcDataDir.getName() + "/mods")
                    : null;
        }

        File jarModsDir = null;
        try {
            java.net.URL location = ModDetector.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File file = resolveLocationToFile(location);
                if (file != null) {
                    if (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip"))) {
                        jarModsDir = file.getParentFile();
                    } else if (file.isDirectory()) {
                        // Dev environment fallback, e.g. build/classes/java/main/
                        File projectDir = file.getParentFile().getParentFile().getParentFile().getParentFile();
                        if (projectDir != null && projectDir.isDirectory()) {
                            File runMods = new File(projectDir, "run/mods");
                            if (runMods.isDirectory()) {
                                jarModsDir = runMods;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // jar location resolution failed; rely on mcDataDir fallback
        }

        return new File[] { modsDir, modsVersionDir, parentModsDir, jarModsDir };
    }

    /**
     * Resolves a {@link java.net.URL} (which may be a {@code file:} or
     * {@code jar:file:} URL) to a {@link File} representing the JAR or directory on
     * disk.
     *
     * <p>
     * {@code jar:file:/path/to/mod.jar!/com/example/Foo.class} cannot be passed
     * directly to {@code new File(url.toURI())} because the URI scheme is
     * {@code jar:}, not {@code file:}. This method handles both cases.
     *
     * @param location the URL to resolve
     * @return the resolved {@link File}, or {@code null} if resolution fails
     */
    private static File resolveLocationToFile(java.net.URL location) {
        if (location == null) {
            return null;
        }
        try {
            String protocol = location.getProtocol();
            if ("file".equals(protocol)) {
                // Simple file: URL — direct conversion is safe.
                return new File(location.toURI());
            } else if ("jar".equals(protocol)) {
                // jar:file:/path/to/mod.jar!/inner/path
                // Open as JarURLConnection and grab the JAR file URL.
                java.net.JarURLConnection juc = (java.net.JarURLConnection) location.openConnection();
                java.net.URL jarFileUrl = juc.getJarFileURL();
                return new File(jarFileUrl.toURI());
            } else {
                // Unknown protocol — attempt toURI() and hope for the best.
                return new File(location.toURI());
            }
        } catch (Exception e) {
            System.out.println("[CrossTie] resolveLocationToFile failed for " + location + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns all detected mod names for diagnostics.
     */
    public Map<String, Boolean> detectAll() {
        Map<String, Boolean> results = new HashMap<>();
        for (String modName : MOD_PATTERNS.keySet()) {
            results.put(modName, isModPresent(modName));
        }
        return results;
    }
}
