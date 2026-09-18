package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.world.chunk.IChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import jp.ngt.rtm.entity.train.EntityBogie;

/**
 * S-5: EntityBogie.getRail() での毎 tick loadChunk() 呼び出しを最適化する Mixin。
 *
 * <p>台車が同一チャンク内にいる限り loadChunk() は不要なため、
 * 前回のチャンク座標と変わった場合のみ実際に loadChunk() を実行する。</p>
 */
@Mixin(value = EntityBogie.class, remap = false)
public abstract class EntityBogieChunkCacheMixin {

    /** 前回 loadChunk() を呼んだチャンク X 座標 */
    @Unique
    private int crosstie$lastLoadChunkX = Integer.MIN_VALUE;

    /** 前回 loadChunk() を呼んだチャンク Z 座標 */
    @Unique
    private int crosstie$lastLoadChunkZ = Integer.MIN_VALUE;

    /**
     * EntityBogie.getRail() 内の IChunkProvider.loadChunk(int, int) 呼び出しをリダイレクトし、
     * 同じチャンク座標であればスキップする。
     */
    @Redirect(
        method = "getRail",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;loadChunk(II)Lnet/minecraft/world/chunk/Chunk;",
            remap = true
        ),
        require = 0
    )
    private net.minecraft.world.chunk.Chunk crosstie$skipRedundantLoadChunk(
            IChunkProvider provider, int cx, int cz) {
        // チャンク座標が前回と同じなら既にロード済みのはずなのでスキップ
        if (cx == this.crosstie$lastLoadChunkX && cz == this.crosstie$lastLoadChunkZ) {
            return null; // 戻り値は getRail() 内で使用されないため null で問題なし
        }
        this.crosstie$lastLoadChunkX = cx;
        this.crosstie$lastLoadChunkZ = cz;
        return provider.loadChunk(cx, cz);
    }
}
