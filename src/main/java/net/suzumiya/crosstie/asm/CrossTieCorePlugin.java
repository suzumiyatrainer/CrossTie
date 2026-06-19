package net.suzumiya.crosstie.asm;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.suzumiya.crosstie.util.ModDetector;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("CrossTieCore")
public class CrossTieCorePlugin implements IFMLLoadingPlugin {

    private static boolean minfoDetected;
    private static ModDetector modDetector;
    private static File mcDataDir;

    public static boolean isMinFoDetected() {
        return minfoDetected;
    }

    public static ModDetector getModDetector() {
        return modDetector;
    }

    /** Minecraft の実行ディレクトリ。{@code injectData()} 完了後に参照可能。 */
    public static File getMcDataDir() {
        return mcDataDir;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "net.suzumiya.crosstie.asm.CrossTieClassTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // Resolve mcDataDir - the Minecraft run directory
        File mcDataDir = null;
        if (data != null) {
            // Try to get the Minecraft data directory from the data map.
            // Forge 1.7.10 provides it under the key "mcDataDir", while
            // vanilla/Forge may use "mcLocation". We fallback to the latter
            // if the former is not present.
            Object mcDir = data.get("mcDataDir");
            if (mcDir instanceof File) {
                mcDataDir = (File) mcDir;
            } else {
                Object mcLoc = data.get("mcLocation");
                if (mcLoc instanceof File) {
                    mcDataDir = (File) mcLoc;
                }
            }
        }

        // Initialize mod detector and scan for MinFo by JAR file name
        CrossTieCorePlugin.mcDataDir = mcDataDir;
        modDetector = new ModDetector(mcDataDir);
        System.out.println("[CrossTieCore] mcDataDir: " + (mcDataDir != null ? mcDataDir.getAbsolutePath() : "null"));
        minfoDetected = modDetector.isModPresent("MinFo");

        System.out.println("[CrossTieCore] MinFo detected: " + minfoDetected);

        if (minfoDetected) {
            disableAngelicaFontRenderer(mcDataDir);
        }
    }

    private void disableAngelicaFontRenderer(File mcDataDir) {
        if (mcDataDir == null) {
            System.out.println("[CrossTieCore] mcDataDir is null, cannot modify angelica-modules.cfg");
            return;
        }

        Path configDir = mcDataDir.toPath().resolve("config");
        Path configFile = configDir.resolve("angelica-modules.cfg");

        if (Files.exists(configFile)) {
            try {
                List<String> lines = Files.readAllLines(configFile);
                boolean modified = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("B:enableFontRenderer=")) {
                        String current = line.substring("B:enableFontRenderer=".length());
                        if (!current.equals("false")) {
                            lines.set(i, "B:enableFontRenderer=false");
                            modified = true;
                            System.out.println(
                                    "[CrossTieCore] Overriding angelica-modules.cfg: enableFontRenderer=false (MinFo conflict)");
                        }
                        break;
                    }
                }
                if (modified) {
                    Files.write(configFile, lines);
                }
                return;
            } catch (IOException e) {
                System.err.println("[CrossTieCore] Failed to modify " + configFile + ": " + e.getMessage());
            }
        }

        // If no config file found, create one
        try {
            Files.createDirectories(configDir);
            String content = "# Angelica modules configuration\n"
                    + "# Modified by CrossTie: MinFo detected, disabling font renderer to prevent conflict\n"
                    + "B:enableFontRenderer=false\n";
            Files.write(configFile, content.getBytes());
            System.out.println(
                    "[CrossTieCore] Created angelica-modules.cfg with enableFontRenderer=false (MinFo conflict)");
        } catch (IOException e) {
            System.err.println("[CrossTieCore] Failed to create angelica-modules.cfg: " + e.getMessage());
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}