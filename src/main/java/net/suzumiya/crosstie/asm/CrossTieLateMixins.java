package net.suzumiya.crosstie.asm;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import java.util.List;
import java.util.Set;

@LateMixin
public class CrossTieLateMixins implements ILateMixinLoader {
    @Override
    public String getMixinConfig() {
        return "mixins.crosstie.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return null;
    }
}
