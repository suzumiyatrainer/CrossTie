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
     */
    @Inject(method = "sendLocationToAllClients", at = @At("HEAD"), cancellable = true)
    private void crosstie$throttleSend(java.util.List par1, CallbackInfo ci) {
        VehicleTrackerEntry self = (VehicleTrackerEntry) (Object) this;
        if (self.ticks % THROTTLED_UPDATE_FREQUENCY != 0) {
            ci.cancel();
        }
    }
}