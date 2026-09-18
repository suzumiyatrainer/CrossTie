package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import org.apache.commons.codec.binary.Base64;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link EntityTrainBase} の DataWatcher から状態バイト列を取得する際の
 * Base64 デコードを tick 内でキャッシュし、GC 圧力を軽減する。
 *
 * <p>問題: {@code getByteFromDataWatcher(int)} は毎 tick 複数回呼ばれるが、
 * そのたびに {@code Base64.decodeBase64()} と新規配列確保が走る。
 *
 * <p>修正: DataWatcher の {@code String} 参照を前回値と比較し、
 * 変化がなければキャッシュ済みの {@code byte[]} を再利用する。
 * 外部依存なし・全環境・両サイド対応。
 */
@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainBaseDataWatcherCacheMixin {

    @Shadow
    protected net.minecraft.entity.DataWatcher dataWatcher;

    /** DataWatcher の DW_ByteArray インデックス (EntityTrainBase の定数を Shadow) */
    @Shadow
    private static int DW_ByteArray;

    @Unique
    private String crosstie$cachedBase64 = null;

    @Unique
    private byte[] crosstie$cachedBytes = null;

    @Inject(
            method = "getByteArray",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void crosstie$cachedGetByteArray(CallbackInfoReturnable<byte[]> cir) {
        String current = this.dataWatcher.getWatchableObjectString(DW_ByteArray);
        if (current != null && current == crosstie$cachedBase64 && crosstie$cachedBytes != null) {
            // 同一の String インスタンス（DataWatcher が更新されていない）→ キャッシュ再利用
            cir.setReturnValue(crosstie$cachedBytes);
            return;
        }
        // キャッシュミスまたは参照が変わった場合はデコードしてキャッシュ更新
        if (current != null) {
            byte[] decoded = Base64.decodeBase64(current);
            crosstie$cachedBase64 = current;
            crosstie$cachedBytes = decoded.length < 16 ? new byte[16] : decoded;
            cir.setReturnValue(crosstie$cachedBytes);
        }
    }
}
