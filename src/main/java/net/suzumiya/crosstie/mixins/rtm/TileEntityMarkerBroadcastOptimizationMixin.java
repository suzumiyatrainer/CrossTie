package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.network.NetworkRegistry;
import jp.ngt.rtm.rail.TileEntityMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TileEntityMarker の setDisplayMode / setMarkersPos が {@code sendToAll} で
 * 全クライアントにブロードキャストする問題を修正する。
 *
 * <h3>問題</h3>
 * <p>
 * {@code setDisplayMode()} は全マーカーのポスト数分 {@code sendToAll(PacketNotice)} を発行し、
 * {@code setMarkersPos()} も {@code sendToAll(PacketMarker)} を発行する。
 * これらはマーカーの物理的な影響範囲と無関係に全クライアントへ到達してしまう。
 * </p>
 *
 * <h3>対策</h3>
 * <p>
 * {@code sendToAllAround} に変更し、マーカー座標中心から 256m 以内のプレイヤーのみに配信する。 典型的なチャンクロード距離
 * (16×12=192m) をカバーする値として 256m を採用。
 * </p>
 */
@Mixin(value = TileEntityMarker.class, remap = false)
public abstract class TileEntityMarkerBroadcastOptimizationMixin {

        private static final double MARKER_PACKET_RANGE = 256.0D;

        /**
         * setDisplayMode 内の sendToAll(PacketNotice) を sendToAllAround に置き換え。
         */
        @Redirect(method = "setDisplayMode", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;sendToAll(Lcpw/mods/fml/common/network/simpleimpl/IMessage;)V", remap = false), require = 0, remap = false)
        private void crosstie$sendDisplayModeAround(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper wrapper,
                        cpw.mods.fml.common.network.simpleimpl.IMessage message) {
                TileEntityMarker self = (TileEntityMarker) (Object) this;
                wrapper.sendToAllAround(message,
                                new NetworkRegistry.TargetPoint(self.getWorldObj().provider.dimensionId, self.xCoord,
                                                self.yCoord, self.zCoord, MARKER_PACKET_RANGE));
        }

        /**
         * setMarkersPos 内の sendToAll(PacketMarker) を sendToAllAround に置き換え。
         */
        @Redirect(method = "setMarkersPos", at = @At(value = "INVOKE", target = "Lcpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper;sendToAll(Lcpw/mods/fml/common/network/simpleimpl/IMessage;)V", remap = false), require = 0, remap = false)
        private void crosstie$sendMarkersPosAround(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper wrapper,
                        cpw.mods.fml.common.network.simpleimpl.IMessage message) {
                TileEntityMarker self = (TileEntityMarker) (Object) this;
                wrapper.sendToAllAround(message,
                                new NetworkRegistry.TargetPoint(self.getWorldObj().provider.dimensionId, self.xCoord,
                                                self.yCoord, self.zCoord, MARKER_PACKET_RANGE));
        }
}
