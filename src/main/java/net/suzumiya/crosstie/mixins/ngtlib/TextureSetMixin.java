package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.model.TextureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = TextureSet.class, remap = false)
public class TextureSetMixin {

    /**
     * Fix StringIndexOutOfBoundsException when loading textures with non-.png extensions (e.g. .gif)
     * or without extension.
     */
    @Redirect(
        method = "<init>(Ljp/ngt/ngtlib/renderer/model/Material;IZ[Ljava/lang/String;)V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/String;indexOf(Ljava/lang/String;)I"
        ),
        require = 0
    )
    private int crosstie$safeExtensionIndex(String textureName, String str) {
        int dotIndex = textureName.lastIndexOf('.');
        if (dotIndex != -1) {
            return dotIndex;
        }
        return textureName.length();
    }
}
