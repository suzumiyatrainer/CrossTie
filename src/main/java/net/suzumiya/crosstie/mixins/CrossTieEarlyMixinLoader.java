package net.suzumiya.crosstie.mixins;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CrossTieEarlyMixinLoader
        implements io.github.tox1cozz.mixinbooterlegacy.IEarlyMixinLoader, com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader {
    // For mixinbooterlegacy
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.crosstie.early.json");
    }

    // For gtnhmixins
    @Override
    public String getMixinConfig() {
        return "mixins.crosstie.early.json";
    }

    @SuppressWarnings("null")
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return new java.util.ArrayList<>();
    }
}
