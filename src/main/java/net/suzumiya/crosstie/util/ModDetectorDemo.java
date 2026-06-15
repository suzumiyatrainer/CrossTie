package net.suzumiya.crosstie.util;

/**
 * Simple demonstration class to manually test {@link ModDetector}.
 *
 * <p>
 * Run with the path to a Minecraft data directory (or a test directory) as the
 * first argument. The program will print whether the MinFo mod is detected and
 * also output the full detection map for all known mods.
 * </p>
 */
public class ModDetectorDemo {
    public static void main(String[] args) {
        // Use the provided argument or default to a test directory named
        // "test_moddetector"
        String path = args.length > 0 ? args[0] : "test_moddetector";
        java.io.File mcDataDir = new java.io.File(path);
        System.out.println("[ModDetectorDemo] Using mcDataDir: " + mcDataDir.getAbsolutePath());
        ModDetector detector = new ModDetector(mcDataDir);
        System.out.println("[ModDetectorDemo] MinFo detected: " + detector.isModPresent("MinFo"));
        System.out.println("[ModDetectorDemo] Full detection results: " + detector.detectAll());
    }
}
