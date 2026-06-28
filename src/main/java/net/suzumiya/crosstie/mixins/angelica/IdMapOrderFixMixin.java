package net.suzumiya.crosstie.mixins.angelica;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.coderbot.iris.shaderpack.materialmap.BlockEntry;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "net.coderbot.iris.shaderpack.IdMap", remap = false)
public abstract class IdMapOrderFixMixin {

    @ModifyVariable(
            method = "parseBlockMap",
            at = @At(value = "STORE", ordinal = 0)
    )
    private static Int2ObjectMap<List<BlockEntry>> crosstie$useLinkedMapForBlocks(Int2ObjectMap<List<BlockEntry>> originalMap) {
        // originalMap is an Int2ObjectOpenHashMap. We throw it away and use a LinkedOpenHashMap
        // to preserve the order of elements from block.properties.
        return new Int2ObjectLinkedOpenHashMap<>();
    }

    @Redirect(
            method = "parseIdMap",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/Object2IntMap;put(Ljava/lang/Object;I)I")
    )
    private static int crosstie$usePutIfAbsentForItems(Object2IntMap<NamespacedId> instance, Object key, int value) {
        // Match Optifine behavior: the first mapping for a specific ID in item.properties wins
        return instance.putIfAbsent((NamespacedId) key, value);
    }
}
