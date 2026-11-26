package net.suzumiya.crosstie.mixins.angelica;

import com.prupe.mcpatcher.ctm.RenderBlockState;
import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.suzumiya.crosstie.compat.recipeplus.IRecipePlusGlassBlock;
import net.suzumiya.crosstie.compat.recipeplus.RecipePlusGlassCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Angelica (MCPatcherForge CTM) の {@code BlockOrientation} において、
 * RecipePlusMod のガラス系拡張ブロック (階段・ハーフ・クォーター) を
 * 元ブロック ({@code Blocks.glass} / {@code Blocks.stained_glass} 等) および
 * 元ブロックの色メタデータとして CTM マッチングさせつつ、
 * <b>同一形状・同一ブロック同士のみ</b> CTM 接続するよう制御する Mixin。
 */
@Mixin(targets = "com.prupe.mcpatcher.ctm.BlockOrientation", remap = false)
public abstract class AngelicaBlockOrientationRecipePlusGlassMixin extends RenderBlockState {

    @Shadow(remap = false)
    private int metadata;

    @Shadow(remap = false)
    private int altMetadata;

    @Shadow(remap = false)
    private int metadataBits;

    @Unique
    private Block crosstie$origBlock;

    @Unique
    private boolean crosstie$remappedRecipePlusGlass;

    @Inject(method = "setBlock", at = @At("RETURN"), remap = false)
    private void crosstie$remapRecipePlusGlassBlock(
            Block block,
            IBlockAccess blockAccess,
            int x,
            int y,
            int z,
            CallbackInfo ci) {
        this.crosstie$origBlock = block;
        if (!(block instanceof IRecipePlusGlassBlock)) {
            this.crosstie$remappedRecipePlusGlass = false;
            return;
        }
        IRecipePlusGlassBlock glass = (IRecipePlusGlassBlock) block;
        Block base = glass.crosstie$getBaseBlock();
        if (RecipePlusGlassCompat.isCullableGlassBase(base)) {
            int baseMeta = glass.crosstie$getBaseBlockMeta();
            this.block = base;
            this.metadata = baseMeta;
            this.altMetadata = baseMeta;
            this.metadataBits = 1 << baseMeta;
            this.crosstie$remappedRecipePlusGlass = true;
        } else {
            this.crosstie$remappedRecipePlusGlass = false;
        }
    }

    @Inject(method = "match", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$matchRemappedRecipePlusGlass(
            BlockStateMatcher matcher,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.crosstie$remappedRecipePlusGlass && matcher != null) {
            cir.setReturnValue(Boolean.valueOf(matcher.match(this.block, this.metadata)));
        }
    }

    @Inject(method = "shouldConnectByBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$connectSameShapeOnlyByBlock(
            Block neighbor,
            int neighborX,
            int neighborY,
            int neighborZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.crosstie$remappedRecipePlusGlass) {
            // 同一ブロック (同形状・同色) 同士のみ接続。異形状 (フル/階段/ハーフ/クォーター間) は接続しない。
            cir.setReturnValue(Boolean.valueOf(this.crosstie$origBlock == neighbor));
        } else if (neighbor instanceof IRecipePlusGlassBlock) {
            // バニラガラス側から RecipePlus ガラス系 (異形状) への接続も禁止
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(method = "shouldConnectByTile", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$connectSameShapeOnlyByTile(
            Block neighbor,
            IIcon origIcon,
            int neighborX,
            int neighborY,
            int neighborZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.crosstie$remappedRecipePlusGlass) {
            if (this.crosstie$origBlock != neighbor) {
                cir.setReturnValue(Boolean.FALSE);
            }
        } else if (neighbor instanceof IRecipePlusGlassBlock) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(method = "copy", at = @At("RETURN"), remap = false)
    private void crosstie$copyRecipePlusGlassState(CallbackInfoReturnable<Object> cir) {
        Object copy = cir.getReturnValue();
        if (copy != null && copy.getClass() == this.getClass()) {
            AngelicaBlockOrientationRecipePlusGlassMixin target = (AngelicaBlockOrientationRecipePlusGlassMixin) copy;
            target.crosstie$origBlock = this.crosstie$origBlock;
            target.crosstie$remappedRecipePlusGlass = this.crosstie$remappedRecipePlusGlass;
        }
    }
}
