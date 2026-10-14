package net.suzumiya.crosstie.mixins.angelica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.gtnewhorizons.angelica.render.WeatherColumns", remap = false)
public interface WeatherColumnsAccessor {

    @Accessor("keyX")
    int[] getKeyX();

    @Accessor("keyZ")
    int[] getKeyZ();
}
