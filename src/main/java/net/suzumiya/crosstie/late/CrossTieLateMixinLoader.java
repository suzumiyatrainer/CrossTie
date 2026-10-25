package net.suzumiya.crosstie.late;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@com.gtnewhorizon.gtnhmixins.LateMixin
@io.github.tox1cozz.mixinbooterlegacy.LateMixin
public class CrossTieLateMixinLoader
        implements io.github.tox1cozz.mixinbooterlegacy.ILateMixinLoader, com.gtnewhorizon.gtnhmixins.ILateMixinLoader {
    // For mixinbooterlegacy
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.crosstie.late.json");
    }

    // For gtnhmixins
    @Override
    public String getMixinConfig() {
        return "mixins.crosstie.late.json";
    }

    @SuppressWarnings("null")
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        java.util.List<String> mixins = new java.util.ArrayList<>();
        if (loadedMods.contains("ATSAssistMod")) {
            mixins.add("atsassist.IFTTTUtilMixin");
            if (cpw.mods.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
                mixins.add("atsassist.GUIIFTTTMaterialPlaySoundFixMixin");
            }
        }
        if (loadedMods.contains("webctc") || loadedMods.contains("WebCTC")) {
            mixins.add("webctc.RailCacheDataUpdateOptimizationMixin");
            mixins.add("webctc.SignalCacheDataUpdateOptimizationMixin");
            mixins.add("webctc.SignalRouterReflectionMixin");
            mixins.add("webctc.RailGroupUtilsSignalLevelMixin");
        }
        return mixins;
    }
}
