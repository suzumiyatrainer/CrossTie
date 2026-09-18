package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
import net.suzumiya.crosstie.physics.PhysicsEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityBogie.class, remap = false)
public abstract class EntityBogiePhysicsMixin {

    @Shadow
    protected abstract void playJointSound();

    /**
     * サウンド再生はメインスレッドで実行する必要があるため、
     * ワーカースレッドから呼ばれた場合はキューに積んで遅延実行する。
     */
    @Inject(method = "playJointSound", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$deferJointSound(CallbackInfo ci) {
        if (PhysicsEngine.isWorkerThread.get()) {
            // 現在はワーカースレッド（ForkJoinPool）
            // サウンド再生はパケット送信などの副作用を伴うため、メインスレッドで行う

            PhysicsEngine.soundQueue.offer(() -> {
                // playJointSound をリフレクションなしで呼ぶため、Shadow されたものを直接実行…
                // したいが Shadow メソッドは private などをそのまま呼べるわけではない。
                // 簡易的に直接 playSound を投げるか、再度キューから取り出して Shadow を呼ぶ。
                // Lambda 内での this.playJointSound() は元のインスタンスを指すのでOK。
                this.playJointSound();
            });
            ci.cancel();
        }
    }
}
