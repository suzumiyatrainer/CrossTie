package net.suzumiya.crosstie.mixins.webctc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

@Mixin(targets = "org.webctc.cache.rail.RailCacheData", remap = false)
public abstract class RailCacheDataUpdateOptimizationMixin {

    /** 1秒あたりに許可する同期チャンクロード数の上限 */
    @Unique
    private static final int CROSSTIE$MAX_LOADS_PER_SECOND = 2;

    @Unique
    private static long crosstie$windowStart = 0L;

    @Unique
    private static int crosstie$loadsInWindow = 0;

    /**
     * WebCTC 1.2.0: update() 内で「キャッシュ上は在線中だが、チャンクが未ロードのレール」を
     * 更新するために呼ばれる world.chunkProvider.loadChunk(key.x / 16, key.z / 16) を制限する。
     *
     * <p>
     * 完全に無効化すると、列車が通過した直後にチャンクがアンロードされたレールが
     * 「在線中(赤)」のキャッシュのまま永久に更新されなくなる（列車と在線表示が不一致になる）ため、
     * 本来の挙動（ロードして最新状態へ更新）を維持しつつ、
     * 1.7.10 の同期チャンクロードによるラグスパイクを避けるため 1秒あたりの回数のみ制限する。
     * 制限を超えた分は次回の update()（約1秒後）に持ち越される。
     * </p>
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
    private Chunk crosstie$limitChunkLoading(IChunkProvider provider, int chunkX, int chunkZ) {
        long now = System.currentTimeMillis();
        if (now - crosstie$windowStart >= 1000L) {
            crosstie$windowStart = now;
            crosstie$loadsInWindow = 0;
        }
        if (crosstie$loadsInWindow >= CROSSTIE$MAX_LOADS_PER_SECOND) {
            return null;
        }
        crosstie$loadsInWindow++;
        return provider.loadChunk(chunkX, chunkZ);
    }
}
