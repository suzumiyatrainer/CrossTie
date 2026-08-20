package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.ModelPackManager;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import jp.ngt.ngtlib.util.NGTUtil;
import net.minecraft.util.ResourceLocation;
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

@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(value = ModelPackManager.class, remap = false)
public class ModelPackManagerReloadMixin {

    @Unique
    public volatile boolean crosstie$isReloading = false;

    @Unique
    public Map<String, Map<String, ModelSetBase>> crosstie$oldAllModelSetMap = new ConcurrentHashMap<>();
    @Unique
    public Map<String, Map<String, ModelSetBase>> crosstie$oldSmpModelSetMap = new ConcurrentHashMap<>();
    @Unique
    public Map<String, ModelSetBase> crosstie$oldDummyMap = new ConcurrentHashMap<>();
    @Unique
    public Map<String, IModelNGT> crosstie$oldModelFileMap = new ConcurrentHashMap<>();
    @Unique
    public Map<String, Map<String, ResourceLocation>> crosstie$oldResourceMap = new ConcurrentHashMap<>();
    @Unique
    public Map<String, String> crosstie$oldScriptCache = new ConcurrentHashMap<>();

    @Inject(method = "getModelSet", at = @At("HEAD"), cancellable = true)
    public <T extends ModelSetBase> void onGetModelSet(String type, String name, CallbackInfoReturnable<T> cir) {
        if (crosstie$isReloading) {
            boolean isSMPClient = NGTUtil.isSMP() && !NGTUtil.isServer();
            Map<String, Map<String, ModelSetBase>> map = isSMPClient ? crosstie$oldSmpModelSetMap : crosstie$oldAllModelSetMap;
            if (map.containsKey(type)) {
                T modelSet = (T) map.get(type).get(name);
                if (modelSet != null) {
                    cir.setReturnValue(modelSet);
                    return;
                }
            }
            cir.setReturnValue((T) crosstie$oldDummyMap.get(type));
        }
    }

    @Inject(method = "getModelList", at = @At("HEAD"), cancellable = true)
    public void onGetModelList(String type, CallbackInfoReturnable<List<ModelSetBase>> cir) {
        if (crosstie$isReloading) {
            Map<String, Map<String, ModelSetBase>> map = NGTUtil.isSMP() ? crosstie$oldSmpModelSetMap : crosstie$oldAllModelSetMap;
            if (map.containsKey(type)) {
                List<ModelSetBase> list = new ArrayList<>(map.get(type).values());
                list.sort(Comparator.comparing(o -> o.getConfig().getName()));
                cir.setReturnValue(list);
            } else {
                cir.setReturnValue(new ArrayList<>());
            }
        }
    }

    @Inject(method = "getModelFile", at = @At("HEAD"), cancellable = true)
    public void onGetModelFile(String key, CallbackInfoReturnable<IModelNGT> cir) {
        if (crosstie$isReloading) {
            cir.setReturnValue(crosstie$oldModelFileMap.get(key));
        }
    }

    @Inject(method = "getResource(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/util/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    public void onGetResource(String domain, String path, CallbackInfoReturnable<ResourceLocation> cir) {
        if (crosstie$isReloading) {
            Map<String, ResourceLocation> map = crosstie$oldResourceMap.get(domain);
            if (map != null && map.containsKey(path)) {
                cir.setReturnValue(map.get(path));
                return;
            }
            cir.setReturnValue(new ResourceLocation(domain, path));
        }
    }
}
