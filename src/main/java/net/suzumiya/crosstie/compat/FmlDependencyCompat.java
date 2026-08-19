package net.suzumiya.crosstie.compat;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;

import java.util.*;

public class FmlDependencyCompat {

    public static void normalizeModRequirements(Loader loader) {
        if (loader == null) return;
        List<ModContainer> activeMods = loader.getActiveModList();
        if (activeMods == null || activeMods.isEmpty()) {
            return;
        }

        // Map lowercase modid -> actual modid (e.g. "rtm" -> "RTM")
        Map<String, String> lowercaseToActual = new HashMap<>();
        for (ModContainer mc : activeMods) {
            if (mc != null && mc.getModId() != null) {
                lowercaseToActual.put(mc.getModId().toLowerCase(Locale.ROOT), mc.getModId());
            }
        }

        for (ModContainer mc : activeMods) {
            if (mc == null) continue;
            ModMetadata meta = mc.getMetadata();
            if (meta == null) continue;

            if (meta.requiredMods != null && !meta.requiredMods.isEmpty()) {
                Set<ArtifactVersion> normalized = new LinkedHashSet<>();
                for (ArtifactVersion av : meta.requiredMods) {
                    normalized.add(normalizeVersion(av, lowercaseToActual, mc.getModId(), "requiredMods"));
                }
                meta.requiredMods = normalized;
            }

            if (meta.dependencies != null && !meta.dependencies.isEmpty()) {
                List<ArtifactVersion> normalized = new ArrayList<>();
                for (ArtifactVersion av : meta.dependencies) {
                    normalized.add(normalizeVersion(av, lowercaseToActual, mc.getModId(), "dependencies"));
                }
                meta.dependencies = normalized;
            }

            if (meta.dependants != null && !meta.dependants.isEmpty()) {
                List<ArtifactVersion> normalized = new ArrayList<>();
                for (ArtifactVersion av : meta.dependants) {
                    normalized.add(normalizeVersion(av, lowercaseToActual, mc.getModId(), "dependants"));
                }
                meta.dependants = normalized;
            }
        }
    }

    private static ArtifactVersion normalizeVersion(ArtifactVersion av, Map<String, String> lowercaseToActual, String modId, String type) {
        if (av == null || av.getLabel() == null) {
            return av;
        }
        String label = av.getLabel();
        String actual = lowercaseToActual.get(label.toLowerCase(Locale.ROOT));
        if (actual != null && !actual.equals(label)) {
            System.out.println("[CrossTie] Normalized mod requirement in " + modId + " (" + type + "): " + label + " -> " + actual);
            String range = av.getRangeString();
            if (range != null && !range.isEmpty() && !range.equals("any")) {
                return VersionParser.parseVersionReference(actual + "@" + range);
            } else {
                return new DefaultArtifactVersion(actual, true);
            }
        }
        return av;
    }
}
