package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.RTMConfig;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.sound.MovingSoundVehicle;
import jp.ngt.rtm.sound.SoundUpdaterVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Phase 4-2: {@link SoundUpdaterVehicle} の遠距離音域外スキップと終了済みサウンドの定期パージ。
 *
 * <p>音域外の列車に対して毎 tick JS スクリプト（{@code onUpdate}）を実行したりサウンドチャンネルを占有し続ける問題を解消する。
 * {@code stopAllSounds()} 単体では {@code playingSounds} や {@code prevSound} が残存するため、
 * 遠距離スキップ時に合わせてクリア・{@code silent = true} へ戻し、再接近時に正常に再再生されるようにする。</p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = SoundUpdaterVehicle.class, remap = false)
public abstract class SoundUpdaterVehicleOptimizationMixin {

    @Shadow
    protected EntityVehicleBase theVehicle;

    @Shadow
    protected boolean silent;

    @Shadow
    protected ResourceLocation prevSoundResource;

    @Shadow
    protected ISound prevSound;

    @Shadow
    protected List<MovingSoundVehicle> playingSounds;

    @Shadow
    public abstract void stopAllSounds();

    @Unique
    private boolean crosstie$outOfRange = false;

    @Unique
    private int crosstie$purgeCounter = 0;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$optimizeSoundUpdate(CallbackInfo ci) {
        if (this.theVehicle == null || this.theVehicle.isDead) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc != null ? (mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer) : null;
        if (viewer != null) {
            double range = Math.max((double) RTMConfig.trainRunningSoundRange, 48.0D) + 16.0D;
            double distSq = this.theVehicle.getDistanceSqToEntity(viewer);
            if (distSq > range * range) {
                if (!this.crosstie$outOfRange || !this.playingSounds.isEmpty() || this.prevSound != null) {
                    this.stopAllSounds();
                    this.playingSounds.clear();
                    this.prevSound = null;
                    this.prevSoundResource = null;
                    this.silent = true;
                    this.crosstie$outOfRange = true;
                }
                ci.cancel();
                return;
            }
            this.crosstie$outOfRange = false;
        }

        // 終了済みサウンドが playingSounds に溜まり続けるリークを 20 tick ごとにパージ
        if (!this.playingSounds.isEmpty() && (++this.crosstie$purgeCounter >= 20)) {
            this.crosstie$purgeCounter = 0;
            for (int i = this.playingSounds.size() - 1; i >= 0; --i) {
                MovingSoundVehicle sound = this.playingSounds.get(i);
                if (sound == null || sound.isDonePlaying()) {
                    this.playingSounds.remove(i);
                }
            }
        }
    }
}
