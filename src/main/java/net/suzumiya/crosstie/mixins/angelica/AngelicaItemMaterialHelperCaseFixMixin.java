package net.suzumiya.crosstie.mixins.angelica;

import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.coderbot.iris.uniforms.ItemMaterialHelper", remap = false)
public abstract class AngelicaItemMaterialHelperCaseFixMixin {

    @Unique
    private static final Map<Item, NamespacedId> crosstie$itemNameCache = new ConcurrentHashMap<>();

    /**
     * getCachedItemName では ResourceLocation を経由することで、MODドメインが大文字小文字区別を
     * 失い小文字化される問題があるため、ItemRegistryから取得した名前をそのまま NamespacedId に渡す。
     * また、高頻度呼び出しに対応するため ConcurrentHashMap でキャッシュを保持する。
     */
    @Inject(method = "getCachedItemName", at = @At("HEAD"), cancellable = true)
    private static void crosstie$getCachedItemNameCaseSensitive(Item item, CallbackInfoReturnable<NamespacedId> cir) {
        if (item == null) {
            return;
        }
        NamespacedId cached = crosstie$itemNameCache.get(item);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }
        Object itemKey = Item.itemRegistry.getNameForObject(item);
        if (itemKey instanceof String) {
            NamespacedId namespacedId = new NamespacedId((String) itemKey);
            crosstie$itemNameCache.put(item, namespacedId);
            cir.setReturnValue(namespacedId);
        }
    }

    /**
     * lookupMaterialId も同様に ResourceLocation の小文字化を回避する。
     */
    @Inject(method = "lookupMaterialId", at = @At("HEAD"), cancellable = true)
    private static void crosstie$lookupMaterialIdCaseSensitive(Item item, int metadata,
            CallbackInfoReturnable<Integer> cir) {
        if (item instanceof ItemBlock) {
            ItemBlock itemBlock = (ItemBlock) item;
            net.minecraft.block.Block block = itemBlock.field_150939_a;

            if (block != null) {
                it.unimi.dsi.fastutil.objects.Reference2ObjectMap<net.minecraft.block.Block, it.unimi.dsi.fastutil.ints.Int2IntMap> blockMetaMatches = net.coderbot.iris.block_rendering.BlockRenderingSettings.INSTANCE
                        .getBlockMetaMatches();
                if (blockMetaMatches != null) {
                    it.unimi.dsi.fastutil.ints.Int2IntMap metaMap = blockMetaMatches.get(block);
                    if (metaMap != null) {
                        int id = net.coderbot.iris.block_rendering.BlockMaterialMapping.resolveId(metaMap,
                                itemBlock.getMetadata(metadata));
                        if (id != -1) {
                            cir.setReturnValue(id);
                            return;
                        }
                    }
                }
            }
        }

        it.unimi.dsi.fastutil.objects.Object2IntFunction<NamespacedId> itemIds = net.coderbot.iris.block_rendering.BlockRenderingSettings.INSTANCE
                .getItemIds();
        if (itemIds != null) {
            NamespacedId name = crosstie$getItemNameCached(item);
            if (name != null) {
                cir.setReturnValue(itemIds.applyAsInt(name));
            }
        }
    }

    @Unique
    private static NamespacedId crosstie$getItemNameCached(Item item) {
        if (item == null) return null;
        NamespacedId cached = crosstie$itemNameCache.get(item);
        if (cached != null) return cached;
        Object itemKey = Item.itemRegistry.getNameForObject(item);
        if (itemKey instanceof String) {
            NamespacedId namespacedId = new NamespacedId((String) itemKey);
            crosstie$itemNameCache.put(item, namespacedId);
            return namespacedId;
        }
        return null;
    }
}
