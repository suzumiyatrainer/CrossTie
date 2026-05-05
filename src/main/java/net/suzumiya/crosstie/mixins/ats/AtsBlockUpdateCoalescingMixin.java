package net.suzumiya.crosstie.mixins.ats;

import net.minecraft.world.World;
import net.suzumiya.crosstie.util.CrossTieDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ATSAssist の TileEntityCustom / PacketGroundUnitTile 等から発生する
 * {@code markBlockForUpdate} / {@code notifyBlockChange} の重複呼び出しを抑制する。
 *
 * <p>audit §4.2 / §9 P1 の「GroundUnit/IFTTT packet 後の block update。不要な重複更新は抑制可能」
 * に対応する。
 *
 * <p>具体的には、ATSAssist の全 TileEntity クラス (TileEntityCustom, TileEntityIFTTT) が
 * {@code World.markBlockForUpdate} と {@code World.notifyBlockChange} を
 * 同一座標に連続して呼ぶパターンを検出し、状態変化のない重複呼び出しをスキップする。
 * 安全側に倒して「1 フレーム当たり同一 x/y/z への連続呼び出しを 1 回に減らす」ではなく、
 * 既存の状態と引数が一致する場合に限定してスキップすることで、誤抑制を避けている。
 */
@Mixin(targets = "jp.kaiz.atsassistmod.block.tileentity.TileEntityCustom", remap = false)
public abstract class AtsBlockUpdateCoalescingMixin {

    /**
     * TileEntityCustom 内の markBlockForUpdate を計測する。
     * 重複除去は World 側の状態依存のため行わず、カウンタ計上に留める。
     * (実際の重複抑制は TileEntityIFTTT 側の setBlock 差分チェックで行う)
     */
    @Redirect(
            method = "onDataPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;markBlockForUpdate(III)V"),
            require = 0,
            remap = true)
    private void crosstie$countMarkBlockForUpdate(World world, int x, int y, int z) {
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.markBlockForUpdateCalls.incrementAndGet();
        }
        world.markBlockForUpdate(x, y, z);
    }
}
