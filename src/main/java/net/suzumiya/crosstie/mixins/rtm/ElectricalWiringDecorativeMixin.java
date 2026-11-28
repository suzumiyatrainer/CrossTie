package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.ElectricalWiringManager;
import jp.ngt.rtm.electric.TileEntityConnector;
import jp.ngt.rtm.electric.TileEntityDummyEW;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * お飾り架線（機能ノードに繋がれていない純装飾の架線ネットワーク）を
 * {@code ElectricalWiringManager.propagateSignal()} から除外し、サーバー MSPT を低減する。
 *
 * <ul>
 *   <li>キャッシュをディメンション別・サーバースレッド専用の {@code ElectricalWiringManager} インスタンス内に保持し、
 *       スレッド競合とワールドアンロード時のメモリリークを完全に排除する。</li>
 *   <li>{@code topologyRevision} の変化で O(1) 自動無効化するため、接続変更・破壊・チャンク読込/解放の無効化漏れがゼロ。</li>
 *   <li>{@code TileEntityConnector} / {@code TileEntityDummyEW} / {@code DIRECT} / {@code TO_ENTITY} /
 *       接続先未ロードチャンク ({@code getTile == null}) を機能ノードとして保護し、連結成分の全ノードを一括キャッシュする。</li>
 * </ul>
 */
@Mixin(value = ElectricalWiringManager.class, remap = false)
public abstract class ElectricalWiringDecorativeMixin {

    @Shadow
    private long topologyRevision;

    @Shadow
    public abstract TileEntityElectricalWiring getTile(int x, int y, int z);

    @Unique
    private final IdentityHashMap<TileEntityElectricalWiring, Boolean> crosstie$decorativeCache =
            new IdentityHashMap<TileEntityElectricalWiring, Boolean>();

    @Unique
    private long crosstie$cachedRevision = Long.MIN_VALUE;

    @Inject(method = "propagateSignal", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipDecorativeNetwork(TileEntityElectricalWiring origin, int level, CallbackInfo ci) {
        if (!CrossTieConfig.decorativeWireOptimizationEnabled || origin == null) {
            return;
        }
        if (origin.getWorldObj() == null || origin.getWorldObj().isRemote) {
            return;
        }

        if (this.crosstie$cachedRevision != this.topologyRevision) {
            this.crosstie$decorativeCache.clear();
            this.crosstie$cachedRevision = this.topologyRevision;
        }

        Boolean cached = this.crosstie$decorativeCache.get(origin);
        if (cached == null) {
            cached = this.crosstie$evaluateAndCacheNetwork(origin);
        }
        if (cached) {
            ci.cancel();
        }
    }

    /**
     * 連結成分全体を BFS 探索し、機能ノード・機能接続・未ロード先が 1 件もなければ {@code true}（お飾り）、
     * そうでなければ {@code false}（機能配線）として到達した全ノードを一括キャッシュする。
     */
    @Unique
    private boolean crosstie$evaluateAndCacheNetwork(TileEntityElectricalWiring origin) {
        IdentityHashMap<TileEntityElectricalWiring, Boolean> visited =
                new IdentityHashMap<TileEntityElectricalWiring, Boolean>();
        Deque<TileEntityElectricalWiring> queue = new ArrayDeque<TileEntityElectricalWiring>();

        visited.put(origin, Boolean.TRUE);
        queue.add(origin);

        boolean isFunctional = false;

        while (!queue.isEmpty()) {
            TileEntityElectricalWiring current = queue.poll();
            if (current instanceof TileEntityConnector || current instanceof TileEntityDummyEW) {
                isFunctional = true;
            }

            List<Connection> connections = current.getConnectionList();
            if (connections == null) {
                continue;
            }
            for (int i = 0, size = connections.size(); i < size; i++) {
                Connection c = connections.get(i);
                if (c == null || c.type == Connection.ConnectionType.NONE || c.type == Connection.ConnectionType.TO_PLAYER) {
                    continue;
                }
                if (c.type == Connection.ConnectionType.DIRECT || c.type == Connection.ConnectionType.TO_ENTITY) {
                    isFunctional = true;
                    continue;
                }

                // WIRE 接続: マネージャーの O(1) レジストリから取得。未ロード(null)は安全側に倒して機能配線扱い。
                TileEntityElectricalWiring next = this.getTile(c.x, c.y, c.z);
                if (next == null) {
                    isFunctional = true;
                } else if (!visited.containsKey(next)) {
                    visited.put(next, Boolean.TRUE);
                    queue.add(next);
                }
            }
        }

        Boolean isDecorative = Boolean.valueOf(!isFunctional);
        for (TileEntityElectricalWiring node : visited.keySet()) {
            this.crosstie$decorativeCache.put(node, isDecorative);
        }
        return !isFunctional;
    }
}
