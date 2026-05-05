package net.suzumiya.crosstie.mixins.ats;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.suzumiya.crosstie.util.CrossTieDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * IFTTT の "Minecraft.SetBlock" アクションで発生する不要な setBlock を差分チェックで抑制する。
 *
 * <p>audit §4.2 / §9 P1:
 * "IFTTT {@code That.Minecraft.SetBlock} は同一 block/meta の場合にスキップする。"
 *
 * <p>WebCTC の {@code RailGroup.update()} と同様のパターン。
 * 対象ブロックが既に同じ Block/meta だった場合は {@code setBlock} を呼ばずに {@code false} を返す。
 * Celeritas の {@code scheduleRebuildForBlockArea} への伝播を防ぐ。
 */
@Mixin(targets = "jp.kaiz.atsassistmod.ifttt.IFTTTContainer$That$Minecraft$SetBlock", remap = false)
public abstract class IFTTTSetBlockDiffMixin {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlock(IIILnet/minecraft/block/Block;II)Z"),
            require = 0,
            remap = true)
    private boolean crosstie$skipUnchangedIftttSetBlock(
            World world, int x, int y, int z, Block block, int metadata, int flags) {
        if (world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == metadata) {
            if (CrossTieDiagnostics.isEnabled()) {
                CrossTieDiagnostics.skippedSetBlockCalls.incrementAndGet();
            }
            return false;
        }
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.blockUpdates.incrementAndGet();
        }
        return world.setBlock(x, y, z, block, metadata, flags);
    }
}
