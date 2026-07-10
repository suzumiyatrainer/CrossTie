package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityTrainBase の総合最適化:
 *
 * <ul>
 *   <li><b>遠距離間引き</b> - クライアント側でプレイヤーから 256m 以上離れた車両の更新頻度を低減</li>
 * </ul>
 *
 * <p>
 * 旧バージョンに存在した {@code getBlock()} キャッシュ (@Redirect) は、
 * {@code EntityTrainBase} に {@code onUpdate()} が定義されていないため
 * （実際は {@code EntityVehicleBase.onVehicleUpdate()} で処理される）、
 * {@code require = 0} のためサイレントスルーとなり一切機能していなかった。
 * このため当該コードは削除した。
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseOptimizationMixin {

    @Unique
    private static final double DISTANT_THRESHOLD_SQ = 256.0D * 256.0D;

    @Unique
    private static final int DISTANT_SKIP_INTERVAL = 2;

    @Unique
    private int crosstie$distantSkipCounter = 0;

    /**
     * クライアント側でプレイヤーから 256m 以上離れた車両の onUpdate を
     * DISTANT_SKIP_INTERVAL ティックに1回だけ実行する。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, require = 0, remap = true)
    private void crosstie$skipDistantUpdate(CallbackInfo ci) {
        if (!CrossTieConfig.trainDistantCullingEnabled) {
            return;
        }
        Entity entity = (Entity) (Object) this;
        if (!entity.worldObj.isRemote) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null) {
            return;
        }
        double dx = entity.posX - mc.renderViewEntity.posX;
        double dz = entity.posZ - mc.renderViewEntity.posZ;
        if (dx * dx + dz * dz > DISTANT_THRESHOLD_SQ) {
            if (++this.crosstie$distantSkipCounter % DISTANT_SKIP_INTERVAL != 0) {
                ci.cancel();
            }
        } else {
            this.crosstie$distantSkipCounter = 0;
        }
    }
}