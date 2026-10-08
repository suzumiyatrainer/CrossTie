package net.suzumiya.crosstie.mixins.dimensionalanchors;

import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "mods.immibis.chunkloader.porting.ChunkLoadInterface132", remap = false)
public class ChunkLoadInterface132AddChunkMixin {

    // DAはチャンク追加時に provider.provideChunk(...) instanceof EmptyChunk を判定して
    // 真なら即座に provider.loadChunk(...) を呼び出して強制ロードしてしまう。
    // ACLの遅延ロード機能(20tick待機によるスパイク軽減)の恩恵を受けるため、
    // ここで null を返すことで instanceof EmptyChunk の判定を false にさせ、
    // DAによる即時 loadChunk 呼び出しをブロックする。
    @Redirect(method = "addChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk crosstie$preventImmediateLoad(IChunkProvider provider, int x, int z) {
        return null;
    }
}
