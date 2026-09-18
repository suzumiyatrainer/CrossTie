package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.TrainState.TrainStateType;
import jp.ngt.rtm.world.RTMChunkManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainChunkLoaderCacheMixin {
    @Shadow private ForgeChunkManager.Ticket ticket;
    @Shadow private Set<ChunkCoordIntPair> loadedChunks;
    @Shadow public net.minecraft.world.World worldObj;
    @Shadow private int prevChunkCoordX;
    @Shadow private int prevChunkCoordZ;
    @Shadow protected abstract boolean requestTicket();
    @Shadow public abstract byte getTrainStateData(byte id);

    @Unique
    private final Set<ChunkCoordIntPair> crosstie$previousLoadedChunks = new HashSet<>();

    /**
     * @author Suzumiya
     * @reason 毎フレーム(移動時)の `forceChunk` 呼び出しを差分更新に最適化し、
     *         不要な `forceChunk` 呼び出しと古いチャンクの `unforceChunk` 漏れを修正。
     */
    @Overwrite
    public void forceChunkLoading(int x, int z) {
        if (this.worldObj.isRemote) {
            return;
        }

        if (this.ticket == null) {
            if (!this.requestTicket()) {
                return;
            }
            // チケットが再取得された場合、前回のキャッシュをクリアして再ロード
            crosstie$previousLoadedChunks.clear();
        }

        if (x == this.prevChunkCoordX && z == this.prevChunkCoordZ && !crosstie$previousLoadedChunks.isEmpty()) {
            return; // チャンク移動なし、かつ初期ロード済みなら何もしない
        }

        // 前回ロードされていたチャンク一覧を保存
        crosstie$previousLoadedChunks.clear();
        crosstie$previousLoadedChunks.addAll(this.loadedChunks);

        // 新しいチャンク一覧を this.loadedChunks に展開
        int rad = this.getTrainStateData((byte) TrainStateType.State_ChunkLoader.id);
        RTMChunkManager.INSTANCE.getChunksAround(this.loadedChunks, x, z, rad);

        // 中心チャンクもセットに追加しておく (ForgeChunkManagerの要件)
        ChunkCoordIntPair myChunk = new ChunkCoordIntPair(x, z);
        this.loadedChunks.add(myChunk);

        // 差分更新 (古いチャンクで新しく範囲外になったものを unforce)
        for (ChunkCoordIntPair prevChunk : crosstie$previousLoadedChunks) {
            if (!this.loadedChunks.contains(prevChunk)) {
                ForgeChunkManager.unforceChunk(this.ticket, prevChunk);
            }
        }

        // 差分更新 (新しいチャンクで前回ロードされていなかったものを force)
        for (ChunkCoordIntPair newChunk : this.loadedChunks) {
            if (!crosstie$previousLoadedChunks.contains(newChunk)) {
                ForgeChunkManager.forceChunk(this.ticket, newChunk);
            }
        }
    }
}
