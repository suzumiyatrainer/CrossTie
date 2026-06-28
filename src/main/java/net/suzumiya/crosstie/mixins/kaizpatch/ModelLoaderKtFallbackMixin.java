package net.suzumiya.crosstie.mixins.kaizpatch;

import jp.kaiz.kaizpatch.fixrtm.modelpack.FIXModelPack;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.VecAccuracy;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "jp.kaiz.kaizpatch.fixrtm.model.ModelLoaderKt", remap = false)
public class ModelLoaderKtFallbackMixin {

    @Redirect(
        method = "loadModel",
        at = @At(
            value = "INVOKE",
            target = "Ljp/kaiz/kaizpatch/fixrtm/model/CachedPolygonModel;createCachedModel(Ljp/kaiz/kaizpatch/fixrtm/modelpack/FIXModelPack;Lnet/minecraft/util/ResourceLocation;Ljp/ngt/ngtlib/renderer/model/VecAccuracy;Ljp/ngt/ngtlib/renderer/model/PolygonModel;)Ljp/ngt/ngtlib/renderer/model/PolygonModel;"
        )
    )
    private static PolygonModel redirectCreateCachedModel(jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel instance, FIXModelPack pack, ResourceLocation resource, VecAccuracy accuracy, PolygonModel model) {
        try {
            return instance.createCachedModel(pack, resource, accuracy, model);
        } catch (Exception e) {
            System.err.println("[CrossTie Mixin] CachedPolygonModel fallback: " + resource);
            return model;
        }
    }
}
