package net.suzumiya.crosstie.mixins.webctc;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.suzumiya.crosstie.utils.WebCtcDirtyTracker;
import org.webctc.common.types.PosInt;
import org.webctc.common.types.rail.LargeRailData;

@Mixin(targets = "org.webctc.cache.rail.RailCacheData", remap = false)
public abstract class RailCacheDataUpdateOptimizationMixin {

    /**
     * WebCTC 1.2.0: update() 内で未ロード区間に列車が存在するときに呼び出される
     * world.chunkProvider.loadChunk(key.x / 16, key.z / 16) をスキップする。
     * 1.7.10 では同期チャンクロードが巨大なラグスパイク（数百ms〜数秒）を引き起こすため、
     * ここでの強制ロードを防止する。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/IChunkProvider;func_73158_c(II)Lnet/minecraft/world/chunk/Chunk;",
                    remap = true
            ),
            remap = false
    )
    private Chunk crosstie$preventChunkLoading(IChunkProvider provider, int chunkX, int chunkZ) {
        return null;
    }

    /**
     * WebCTC 1.2.0: update() 冒頭の
     * railMapCache.filter { !world.getChunkFromBlockCoords(...).isChunkLoaded }
     * において、isChunkLoaded の読み出しを常に false に偽装する。
     *
     * 【理由】
     * 元の実装ではロード済みチャンクのレールを一旦すべて除外して loadedTileEntityList から全再構築していたが、
     * 差分更新（案2A）では loadedTileEntityList から Dirty なレールしか返さないため、
     * ロード済みチャンクの未変更レールを coreList に残し続ける必要がある。
     * 常に false を返すことで、未ロード・ロード済み問わず全レールが coreList に保持され、
     * 未ロードチャンクのレールがマップから消える問題も完全に防止される。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/chunk/Chunk;field_76636_d:Z",
                    remap = true
            ),
            remap = false
    )
    private boolean crosstie$keepAllCachedRailsInCoreList(Chunk chunk) {
        return false;
    }

    /**
     * WebCTC 1.2.0: update() 内の world.loadedTileEntityList へのアクセスをインターセプトし、
     * 初回は全件、2回目以降は状態変化（占有・形状・転轍機）があった Dirty な TileEntityLargeRailCore のみを提供する。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/World;field_147482_g:Ljava/util/List;",
                    remap = true
            ),
            remap = false
    )
    private List<?> crosstie$redirectLoadedTileEntityList(World world) {
        return WebCtcDirtyTracker.pollDirtyRailsOrAll(world.loadedTileEntityList);
    }

    /**
     * WebCTC 1.2.0: coreList.putAll(dirtyData) をインターセプトし、
     * 破壊されたレールを coreList から削除した上で、変更があった Dirty レールを反映する。
     *
     * これにより、破壊されたレールは coreList から確実に除外され、
     * 後続の else if (railMapCache.any { !coreList.contains(it.key) }) にて
     * railMapCache が更新され markDirty() が呼ばれてマップから正常に消去される。
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;putAll(Ljava/util/Map;)V"
            ),
            remap = false
    )
    private void crosstie$onCoreListPutAll(Map<PosInt, LargeRailData> coreList, Map<PosInt, LargeRailData> dirtyData) {
        coreList.putAll(dirtyData);
        List<Long> removed = WebCtcDirtyTracker.pollRemovedRails();
        if (!removed.isEmpty()) {
            for (Long packed : removed) {
                int x = WebCtcDirtyTracker.unpackX(packed);
                int y = WebCtcDirtyTracker.unpackY(packed);
                int z = WebCtcDirtyTracker.unpackZ(packed);
                coreList.remove(new PosInt(x, y, z));
            }
        }
    }
}
