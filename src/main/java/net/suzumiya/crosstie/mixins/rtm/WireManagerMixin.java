package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.WireManager;
import net.minecraft.world.ChunkCoordIntPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S-3: WireManager.getWireY() での毎回 {@code new ChunkCoordIntPair} アロケーションを排除する Mixin。
 *
 * <p>元の実装はマップルックアップのたびに {@code new ChunkCoordIntPair(cX, cZ)} を生成していた。
 * long エンコードしたキーで代用することで、架線チェック呼び出しごとのアロケーションをゼロにする。</p>
 *
 * <p>内部的に {@code HashMap<ChunkCoordIntPair, List<WireEntry>>} を
 * {@code HashMap<Long, List<WireEntry>>} に置き換えるため、
 * {@code addWire}/{@code removeWire} 系の処理とセットで置き換える必要がある。
 * ここでは最も高頻度な {@code getWireY()} のみを最適化する（addWire は低頻度のため元のまま）。</p>
 */
@Mixin(value = WireManager.class, remap = false)
public abstract class WireManagerMixin {

    /** 元の loadedWires マップへの shadow (ChunkCoordIntPair → List<WireEntry>) */
    @Shadow
    private Map<ChunkCoordIntPair, List<WireManager.WireEntry>> loadedWires;

    /**
     * long キーでルックアップするためのセカンダリキャッシュ。
     * 内容は {@code loadedWires} と同期される。
     * こちらは新規アロケーションなしで高速ルックアップ可能。
     */
    @Unique
    private final Map<Long, List<WireManager.WireEntry>> crosstie$fastWireMap = new HashMap<>();

    /** チャンク座標を long にエンコード */
    @Unique
    private static long crosstie$toKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * @author CrossTie
     * @reason 毎回の new ChunkCoordIntPair アロケーションを long キーに置き換えて排除する
     */
    @Overwrite(remap = false)
    public double getWireY(float yaw, double x, double y, double z) {
        int cX = MathHelper.floor_double(x) >> 4;
        int cZ = MathHelper.floor_double(z) >> 4;

        // long キーでルックアップ (アロケーションなし)
        long key = crosstie$toKey(cX, cZ);
        List<WireManager.WireEntry> list = crosstie$fastWireMap.get(key);

        // キャッシュにない場合は元のマップから移入
        if (list == null) {
            list = loadedWires.get(new ChunkCoordIntPair(cX, cZ));
            if (list != null) {
                crosstie$fastWireMap.put(key, list);
            }
        }

        if (list != null) {
            double minY = y;
            boolean found = false;
            for (WireManager.WireEntry entry : list) {
                if (entry.inRange(yaw, x, y, z)) {
                    // WireManager.SPLIT = 512
                    int index = entry.lineXZ.getNearlestPoint(512, x, z);
                    double wireY = entry.minY + (entry.maxY - entry.minY) * ((double) index / 512.0D)
                                   + jp.ngt.rtm.entity.train.EntityTrainBase.TRAIN_HEIGHT;
                    if (!found || wireY < minY) {
                        minY = wireY;
                        found = true;
                    }
                }
            }
            if (found) return minY;
        }
        return y;
    }

    /**
     * loadedWires への追加時に fastWireMap も更新する。
     * ただし WireManager.addWire() は低頻度（接続時のみ）のため、
     * ここでは単純に fastWireMap をリセットしてキャッシュを無効化する戦略をとる。
     */
    @org.spongepowered.asm.mixin.injection.Inject(
        method = "addWire",
        at = @org.spongepowered.asm.mixin.injection.At("RETURN"),
        require = 0,
        remap = false
    )
    private void crosstie$onAddWire(
            jp.ngt.rtm.electric.TileEntityElectricalWiring tileEntity,
            jp.ngt.rtm.electric.Connection connection,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        crosstie$fastWireMap.clear();
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "removeWire",
        at = @org.spongepowered.asm.mixin.injection.At("RETURN"),
        require = 0,
        remap = false
    )
    private void crosstie$onRemoveWire(
            jp.ngt.rtm.electric.TileEntityElectricalWiring tileEntity,
            jp.ngt.rtm.electric.Connection connection,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        crosstie$fastWireMap.clear();
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "clear",
        at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
        require = 0,
        remap = false
    )
    private void crosstie$onClear(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        crosstie$fastWireMap.clear();
    }
}
