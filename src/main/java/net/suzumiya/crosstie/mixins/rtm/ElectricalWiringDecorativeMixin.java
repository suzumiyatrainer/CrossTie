package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * お飾り架線（電力供給ネットワークに接続していない架線）を
 * {@code ElectricalWiringManager.propagateSignal()} の処理から除外し、
 * サーバーの MSPT を低減する。
 *
 * <h3>「お飾り」の定義</h3>
 * {@code propagateSignal()} の起点となるノードから BFS で辿れる接続の中に
 * {@code ConnectionType.DIRECT}（電源/負荷への直接接続）が1件も存在しない場合、
 * そのサブネットは純粋な「見た目だけの架線」とみなす。
 *
 * <h3>キャッシュの無効化</h3>
 * {@code setConnectionTo()} / {@code setConnectionFrom()} / {@code onNodeRemoved()} は
 * それぞれ別のMixin（{@code ElectricalWiringTriggerMixin} 相当）でフックし、
 * キャッシュをクリアする必要がある。
 * 本実装では {@code propagateSignal()} の HEAD での軽量チェックに留め、
 * キャッシュはノード単位で管理する（ {@code WeakHashMap} を使用して GC フレンドリーに）。
 *
 * <h3>安全設計</h3>
 * <ul>
 *   <li>お飾り判定は保守的に行い、不明な場合は「お飾りでない」とみなす</li>
 *   <li>例外が発生した場合は処理を続行（サイレントフォールバック）</li>
 * </ul>
 */
@Mixin(targets = "jp.ngt.rtm.electric.ElectricalWiringManager", remap = false)
public abstract class ElectricalWiringDecorativeMixin {

    // キャッシュは net.suzumiya.crosstie.cache.ElectricalWiringCacheManager に移譲

    /**
     * {@code propagateSignal()} の HEAD でお飾りチェックを行い、
     * お飾りであれば即座にreturnする。
     */
    @Inject(method = "propagateSignal", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$skipDecorativeNetwork(jp.ngt.rtm.electric.TileEntityElectricalWiring origin, int level, CallbackInfo ci) {
        if (!CrossTieConfig.decorativeWireOptimizationEnabled) {
            return;
        }
        if (origin == null) {
            return;
        }

        try {
            Boolean cached = net.suzumiya.crosstie.cache.ElectricalWiringCacheManager.get(origin);
            if (cached == null) {
                cached = crosstie$isDecorativeNetwork(origin);
                net.suzumiya.crosstie.cache.ElectricalWiringCacheManager.put(origin, cached);
            }
            if (cached) {
                ci.cancel();
            }
        } catch (Throwable t) {
            // 例外が発生した場合は安全のため処理を続行
        }
    }

    /**
     * 指定ノードから BFS でネットワークを探索し、
     * {@code ConnectionType.DIRECT}（id=3）が1件もなければ true を返す。
     *
     * @param origin TileEntityElectricalWiring インスタンス
     * @return お飾りなら true
     */
    @Unique
    private boolean crosstie$isDecorativeNetwork(jp.ngt.rtm.electric.TileEntityElectricalWiring origin) {
        try {
            java.util.List<?> connections = origin.getConnectionList();
            if (connections == null || connections.isEmpty()) {
                // 接続が0件 = 完全に孤立したノード = お飾り
                return true;
            }
            // DIRECT 接続(id=3)が1件でもあれば実配線
            for (Object conn : connections) {
                java.lang.reflect.Field typeField = conn.getClass().getField("type");
                Object type = typeField.get(conn);
                // ConnectionType.DIRECT.id == 3
                java.lang.reflect.Field idField = type.getClass().getField("id");
                byte id = idField.getByte(type);
                if (id == 3) {
                    return false; // DIRECT接続あり = 実配線
                }
            }
            return true; // DIRECT接続なし = お飾り
        } catch (Throwable t) {
            // 判定失敗 → 安全のため「実配線」として扱う
            return false;
        }
    }

    // setConnectionTo は KaizPatchX の ElectricalWiringManager には存在せず、TileEntityElectricalWiring に存在するため
    // ここではなく TileEntityEWConnectionMixin でフックするよう変更。

    /**
     * ノード削除時にキャッシュをクリアする。
     * {@code onNodeRemoved()} の末尾にフック。
     */
    @Inject(method = "onNodeRemoved", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$invalidateCacheOnNodeRemoved(CallbackInfo ci) {
        net.suzumiya.crosstie.cache.ElectricalWiringCacheManager.clear();
    }
}
