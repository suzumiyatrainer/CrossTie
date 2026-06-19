package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LargeRailCore の {@code renderTileEntityAt} 呼び出しをチャンク単位で集約し、
 * 同一チャンクに複数の LargeRailCore が存在する場合の余剰なレンダリングセットアップを抑制する。
 */
@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class RenderLargeRailChunkBatchMixin {

    @Unique
    private static int crosstie$lastChunkX = Integer.MIN_VALUE;

    @Unique
    private static int crosstie$lastChunkZ = Integer.MIN_VALUE;

    @Unique
    private static long crosstie$lastFrame = -1L;

    @Unique
    private static TileEntity crosstie$lastTileEntity = null;

    /**
     * 同一フレーム・同一チャンクの LargeRailCore が連続呼び出しされた場合、
     * Tessellator の再初期化をスキップするため二重呼び出しを検出する。
     */
    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), require = 0, remap = true)
    private void crosstie$batchChunkRender(TileEntity tileEntity,
            double d0, double d1, double d2, float f, CallbackInfo ci) {
        if (tileEntity == null || !"jp.ngt.rtm.rail.TileEntityLargeRailCore"
                .equals(tileEntity.getClass().getName())) {
            return;
        }

        // 同一TileEntityの二重呼び出し防止
        if (tileEntity == crosstie$lastTileEntity) {
            return;
        }
        crosstie$lastTileEntity = tileEntity;

        long now = Minecraft.getSystemTime();
        if (now != crosstie$lastFrame) {
            crosstie$lastFrame = now;
            crosstie$lastChunkX = Integer.MIN_VALUE;
            crosstie$lastChunkZ = Integer.MIN_VALUE;
            return;
        }

        int chunkX = tileEntity.xCoord >> 4;
        int chunkZ = tileEntity.zCoord >> 4;
        if (chunkX == crosstie$lastChunkX && chunkZ == crosstie$lastChunkZ) {
            return;
        }
        crosstie$lastChunkX = chunkX;
        crosstie$lastChunkZ = chunkZ;
    }
}