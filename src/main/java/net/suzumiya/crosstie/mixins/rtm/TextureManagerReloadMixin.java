package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.texture.TextureManager;
import jp.ngt.rtm.modelpack.texture.TextureProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = TextureManager.class, remap = false)
public class TextureManagerReloadMixin {

    @Unique
    public volatile boolean crosstie$isReloading = false;

    @Unique
    public Map<TextureManager.TexturePropertyType, Map<String, TextureProperty>> crosstie$oldAllTextureMap = new ConcurrentHashMap<>();

    @Inject(method = "getProperty", at = @At("HEAD"), cancellable = true)
    public <T extends TextureProperty> void onGetProperty(TextureManager.TexturePropertyType type, String key, CallbackInfoReturnable<T> cir) {
        if (crosstie$isReloading) {
            if (crosstie$oldAllTextureMap.containsKey(type)) {
                cir.setReturnValue((T) crosstie$oldAllTextureMap.get(type).get(key));
            } else {
                cir.setReturnValue(null);
            }
        }
    }

    @Inject(method = "getTextureList", at = @At("HEAD"), cancellable = true)
    public void onGetTextureList(TextureManager.TexturePropertyType type, CallbackInfoReturnable<List<TextureProperty>> cir) {
        if (crosstie$isReloading) {
            if (crosstie$oldAllTextureMap.containsKey(type)) {
                List<TextureProperty> list = new ArrayList<>(crosstie$oldAllTextureMap.get(type).values());
                list.sort(Comparator.comparing(o -> o.texture));
                cir.setReturnValue(list);
            } else {
                cir.setReturnValue(new ArrayList<>());
            }
        }
    }
}
