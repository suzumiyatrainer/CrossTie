package net.suzumiya.crosstie.mixins.angelica;

import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.coderbot.iris.block_rendering.BlockMaterialMapping", remap = false)
public abstract class AngelicaBlockMaterialMappingCaseFixMixin {

    /**
     * Minecraft 1.7.10 の FML では、MODのブロックID（ドメイン部分）が大文字小文字を区別（維持）して登録される場合があります。
     * 例：`BambooMod:spaWater` Angelica は標準で ResourceLocation
     * を経由してドメインを小文字に変換してしまうため（例：`bamboomod:spaWater`）、 FMLのレジストリからの検索に失敗し、shaderの
     * properties 指定が無視されるバグがあります。 ここでは ResourceLocation
     * の小文字変換をバイパスし、そのままの文字列で検索するように修正します。
     */
    @Inject(method = "resolveBlockOrNull", at = @At("HEAD"), cancellable = true)
    private static void crosstie$resolveBlockCaseSensitive(NamespacedId id, CallbackInfoReturnable<Block> cir) {
        String queryNamespace = id.getNamespace();
        String queryName = id.getName();

        for (Object keyObj : Block.blockRegistry.getKeys()) {
            if (keyObj instanceof String) {
                String key = (String) keyObj;
                int colonIdx = key.indexOf(':');
                if (colonIdx != -1) {
                    String regNamespace = key.substring(0, colonIdx);
                    String regName = key.substring(colonIdx + 1);
                    if (regNamespace.equalsIgnoreCase(queryNamespace) && regName.equalsIgnoreCase(queryName)) {
                        Object blockObj = Block.blockRegistry.getObject(key);
                        if (blockObj instanceof Block && blockObj != Blocks.air) {
                            System.out.println("[CrossTie DEBUG] Found block " + key + " for " + id);
                            cir.setReturnValue((Block) blockObj);
                            return;
                        }
                    }
                } else {
                    if (key.equalsIgnoreCase(queryName)) {
                        Object blockObj = Block.blockRegistry.getObject(key);
                        if (blockObj instanceof Block && blockObj != Blocks.air) {
                            System.out.println("[CrossTie DEBUG] Found block " + key + " for " + id);
                            cir.setReturnValue((Block) blockObj);
                            return;
                        }
                    }
                }
            }
        }
        System.out.println("[CrossTie DEBUG] Block NOT found for " + id);
    }
}
