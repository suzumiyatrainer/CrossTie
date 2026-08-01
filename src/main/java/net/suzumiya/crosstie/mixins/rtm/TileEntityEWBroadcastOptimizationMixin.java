package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TileEntityElectricalWiring および TileEntityDummyEW の接続変更時に発行される
 * {@code sendToAll(PacketWire)} を近距離プレイヤーへの配信に限定する。
 *
 * <h3>問題</h3>
 * <p>
 * 接続変更（setConnectionTo / setConnectionFrom / onRightClick）のたびに
 * {@code NETWORK_WRAPPER.sendToAll(new PacketWire(this))} が実行され、
 * サーバー上の全クライアントへパケットを送信していた。 大規模なワイヤー接続作業（ワールドロード直後のバルク更新など）では特に顕著。
 * </p>
 *
 * <h3>対策</h3>
 * <p>
 * {@code sendToAll} を {@code sendToAllAround} に変更し、 架線ブロック座標中心から 256m
 * 以内のプレイヤーのみに配信する。 PacketWire はビジュアル同期用（クライアント描画更新）であり、 遠距離プレイヤーへの配信は不要。
 * </p>
 */
@Mixin(value = TileEntityElectricalWiring.class, remap = false)
public abstract class TileEntityEWBroadcastOptimizationMixin {

    private static final double EW_PACKET_RANGE = 256.0D;

    /**
     * setConnectionTo 内の sendToAll(PacketWire) を sendToAllAround に置き換え。
     * setConnectionFrom からも同じパスを通るため、まとめて 1 つの @Redirect で対応。
     */
    @Redirect(method = { "setConnectionTo", "setConnectionFrom",
            "onRightClick" }, at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;sendToAll(Lcpw/mods/fml/common/network/simpleimpl/IMessage;)V", remap = false), require = 0, remap = false)
    private void crosstie(SimpleNetworkWrapper wrapper, IMessage message) {
        TileEntityElectricalWiring self = (TileEntityElectricalWiring) (Object) this;
        if (self.getWorldObj() != null) {
            wrapper.sendToAllAround(message, new NetworkRegistry.TargetPoint(self.getWorldObj().provider.dimensionId,
                    self.xCoord, self.yCoord, self.zCoord, EW_PACKET_RANGE));
        } else {
            // ワールドが取得できない場合は安全のため sendToAll にフォールバック
            wrapper.sendToAll(message);
        }
    }
}
