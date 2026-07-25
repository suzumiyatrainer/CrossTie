package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.vehicle.VehicleTrackerEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityTrackerの更新頻度を低減し、サーバー→クライアントのパケット負荷を軽減します。
 */
@Mixin(targets = "jp.ngt.rtm.entity.vehicle.VehicleTrackerEntry", remap = false)
public abstract class VehicleTrackerThrottleMixin {

    @Unique
    private static final int THROTTLED_UPDATE_FREQUENCY = 3;

    /**
     * VehicleTrackerEntry.<init>(EntityTrackerEntry, Entity) を書き換え、
     * コピー元の updateFrequency を 3 に強制します。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void crosstie$throttleUpdateFrequency(EntityTrackerEntry par1, Entity par2, CallbackInfo ci) {
        // コピー元で実際に参照されているのは this.updateFrequency なので、
        // ここでは既に super() で反映済み。直接 field を set するには Shadow が必要。
        // 実効的な制御は sendLocationToAllClients 側の Inject で行う。
    }

    /**
     * 送信ループの間引き条件を書き換え：updateFrequency に関わらず THROTTLED_UPDATE_FREQUENCY で送信。
     *
     * <h3>VehicleTrackerEntryMixin との複合動作について</h3>
     * <p>
     * {@code sendLocationToAllClients} は CrossTie 内で 2 つの Mixin が作用する：
     * <ol>
     *   <li><b>このMixin（HEAD/cancel）：</b>
     *       {@code ticks % 3 != 0} の場合はメソッド全体をキャンセルし、
     *       {@code VehicleTrackerEntryMixin} の {@code @Redirect} にも到達しない。</li>
     *   <li><b>VehicleTrackerEntryMixin（@Redirect）：</b>
     *       通過した場合（3tick周期）に {@code sendToAll} を個別送信へ置き換え。
     *       追跡中プレイヤーには毎回 {@code sendTo}、非追跡プレイヤーには
     *       {@code ticks % 10 == 0} のみ送信する。</li>
     * </ol>
     * この結果、<b>非追跡プレイヤーへの位置パケット送信は実質 3 × 10 = 30tick（1.5秒）に1回</b>となる。
     * 追跡中プレイヤーへは 3tick に1回。これは意図した設計である。
     * </p>
     */
    @Inject(method = "sendLocationToAllClients", at = @At("HEAD"), cancellable = true)
    private void crosstie$throttleSend(java.util.List<?> par1, CallbackInfo ci) {
        VehicleTrackerEntry self = (VehicleTrackerEntry) (Object) this;
        if (self.ticks % THROTTLED_UPDATE_FREQUENCY != 0) {
            ci.cancel();
        }
    }
}