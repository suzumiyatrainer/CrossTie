package net.suzumiya.crosstie.mixins.atsassist;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "jp.kaiz.atsassistmod.ifttt.IFTTTUtil", remap = false)
public abstract class IFTTTUtilMixin {

    @Unique
    private static final ObjectMapper crosstie$objectMapper = new ObjectMapper();

    /**
     * @author CrossTie
     * @reason Redirect ObjectMapper creation to a static singleton instance to avoid repeated reflection analysis.
     */
    @Redirect(
            method = "convertClassSafe(Ljp/kaiz/atsassistmod/ifttt/IFTTTContainer;)[B",
            at = @At(value = "NEW", target = "com/fasterxml/jackson/databind/ObjectMapper"),
            remap = false
    )
    private static ObjectMapper redirectObjectMapperNew1() {
        return crosstie$objectMapper;
    }

    /**
     * @author CrossTie
     * @reason Redirect ObjectMapper creation to a static singleton instance to avoid repeated reflection analysis.
     */
    @Redirect(
            method = "convertClassSafe([B)Ljp/kaiz/atsassistmod/ifttt/IFTTTContainer;",
            at = @At(value = "NEW", target = "com/fasterxml/jackson/databind/ObjectMapper"),
            remap = false
    )
    private static ObjectMapper redirectObjectMapperNew2() {
        return crosstie$objectMapper;
    }
}
