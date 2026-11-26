package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.ctm.CTMUtils;
import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.suzumiya.crosstie.compat.recipeplus.IRecipePlusGlassBlock;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica (MCPatcherForge CTM) の {@link CTMUtils#hasCandidates} で、
 * RecipePlusMod のガラス系拡張ブロック (階段・ハーフ・クォーター) に対しても 元ブロック ({@code Blocks.glass} /
 * {@code Blocks.stained_glass} 等) の {@code matchBlocks} CTM 定義を候補として認識させる
 * Mixin。
 */
@Mixin(value = CTMUtils.class, remap = false)
public abstract class AngelicaCtmRecipePlusGlassMixin {

    @Unique
    private static Field crosstie$overridesField;
    @Unique
    private static Field crosstie$blockMapField;
    @Unique
    private static boolean crosstie$fieldsResolved;

    @Inject(method = "hasCandidates", at = @At("RETURN"), cancellable = true, remap = false)
    private static void crosstie$checkRecipePlusGlassBaseCandidates(Block block, IIcon icon,
            CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (!(block instanceof IRecipePlusGlassBlock)) {
            return;
        }
        Block base = ((IRecipePlusGlassBlock) block).crosstie$getBaseBlock();
        if (base == null || base == block || !RecipePlusGlassCompat.isCullableGlassBase(base)) {
            return;
        }
        try {
            if (!crosstie$fieldsResolved) {
                crosstie$fieldsResolved = true;
                Field of = CTMUtils.class.getDeclaredField("overrides");
                of.setAccessible(true);
                crosstie$overridesField = of;
            }
            if (crosstie$overridesField == null) {
                return;
            }
            Object overridesObj = crosstie$overridesField.get(null);
            if (overridesObj == null) {
                return;
            }
            if (crosstie$blockMapField == null) {
                Field bf = overridesObj.getClass().getDeclaredField("block");
                bf.setAccessible(true);
                crosstie$blockMapField = bf;
            }
            @SuppressWarnings("unchecked")
            Map<Block, List<BlockStateMatcher>> blockMap = (Map<Block, List<BlockStateMatcher>>) crosstie$blockMapField
                    .get(overridesObj);
            if (blockMap != null && blockMap.get(base) != null) {
                cir.setReturnValue(Boolean.TRUE);
            }
        } catch (Throwable ignored) {
        }
    }
}
