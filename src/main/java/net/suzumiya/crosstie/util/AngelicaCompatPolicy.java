package net.suzumiya.crosstie.util;

import cpw.mods.fml.common.Loader;
import net.suzumiya.crosstie.config.CrossTieConfig;

/**
 * Central policy for risky Angelica compatibility shims.
 */
public final class AngelicaCompatPolicy {

    private AngelicaCompatPolicy() {
    }

    /**
     * hi03 rail rendering originally bypassed Angelica and forced native legacy GL calls.
     * That path is unsafe on Angelica/core-profile setups, so keep it disabled unless the
     * user explicitly re-enables it.
     */
    public static boolean shouldUseHi03LegacyDisplayLists() {
        return !Loader.isModLoaded("angelica")
                || CrossTieConfig.enableHi03LegacyAngelicaDisplayLists
                || CrossTieConfig.enableHi03LegacyAngelicaBypass;
    }

    /**
     * Full raw-GL bypass for hi03. This is faster, but much riskier than display-list-only mode.
     */
    public static boolean shouldUseHi03LegacyBypass() {
        return !Loader.isModLoaded("angelica") || CrossTieConfig.enableHi03LegacyAngelicaBypass;
    }
}
