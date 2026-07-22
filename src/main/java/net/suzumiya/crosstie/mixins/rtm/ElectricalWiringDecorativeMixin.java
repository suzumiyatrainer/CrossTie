package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.TileEntityConnector;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.cache.ElectricalWiringCacheManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * お飾り架線（BlockConnector に繋がれていない、純粋な BlockInsulator のみの架線）を
 * {@code ElectricalWiringManager.propagateSignal()} の処理から除外し、
 * サーバーの MSPT を大幅に低減します。
 */
@Mixin(targets = "jp.ngt.rtm.electric.ElectricalWiringManager", remap = false)
public abstract class ElectricalWiringDecorativeMixin {

    /**
     * {@code propagateSignal()} の HEAD でお飾りチェックを行い、
     * 純碍子ネットワークであれば即座に return する。
     */
    @Inject(method = "propagateSignal", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipDecorativeNetwork(TileEntityElectricalWiring origin, int level, CallbackInfo ci) {
        if (!CrossTieConfig.decorativeWireOptimizationEnabled) {
            return;
        }
        if (origin == null) {
            return;
        }

        try {
            Boolean cached = ElectricalWiringCacheManager.get(origin);
            if (cached == null) {
                cached = crosstie$isDecorativeNetwork(origin);
                ElectricalWiringCacheManager.put(origin, cached);
            }
            if (cached) {
                ci.cancel();
            }
        } catch (Throwable t) {
            // 例外発生時は安全のためキャンセルしない
        }
    }

    /**
     * 指定ノードから BFS でネットワークを探索し、
     * TileEntityConnector (BlockConnector) や ConnectionType.DIRECT が1件もなければ true (お飾り) を返す。
     */
    @Unique
    private boolean crosstie$isDecorativeNetwork(TileEntityElectricalWiring origin) {
        if (origin instanceof TileEntityConnector) {
            return false; // 起点自体がコネクタ = 機能ノード
        }

        Set<TileEntityElectricalWiring> visited = new HashSet<>();
        Deque<TileEntityElectricalWiring> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);

        while (!queue.isEmpty()) {
            TileEntityElectricalWiring current = queue.poll();
            if (current instanceof TileEntityConnector) {
                return false; // コネクタに到達 = 機能配線
            }

            List<Connection> connections = current.getConnectionList();
            if (connections != null) {
                for (Connection c : connections) {
                    if (c.type == Connection.ConnectionType.NONE || c.type == Connection.ConnectionType.TO_PLAYER) {
                        continue;
                    }
                    if (c.type == Connection.ConnectionType.DIRECT) {
                        return false; // DIRECT接続あり = 機能配線
                    }

                    TileEntityElectricalWiring next = c.getElectricalWiring(current.getWorldObj());
                    if (next != null && visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }

        return true; // コネクタ・DIRECT接続が一切見つからない = 純装飾（碍子）配線
    }

    @Inject(method = "onNodeRemoved", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$invalidateCacheOnNodeRemoved(CallbackInfo ci) {
        ElectricalWiringCacheManager.clear();
    }
}
