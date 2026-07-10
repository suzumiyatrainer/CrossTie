package net.suzumiya.crosstie.mixins.atsassist;

import jp.kaiz.atsassistmod.block.tileentity.TileEntityIFTTT;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.script.ScriptEngine;

/**
 * ターゲット: jp.kaiz.atsassistmod.ifttt.IFTTTContainer$That$ATSAssist$JavaScript
 *
 * ATSAssistMod本体の doThat() 実装には、以下2点のバグがある:
 *  1) error フラグが一度 true になると、NBTに永続化され、以後
 *     setJSText()(GUI編集)以外にリセットする手段が無く、事実上恒久停止する。
 *  2) scriptEngine が一度コンパイルされると使い回され続け、
 *     setJSText() では scriptEngine が null に戻されないため、
 *     壊れたスクリプト内容のままのエンジンが焼き付く。
 *
 * 本Mixinは、doThat() の先頭で一定間隔ごとに error/scriptEngine を
 * リセットし、現在の jsText から再コンパイルする機会を毎回作ることで、
 * 「一度エラーになると二度と実行されない」状態を解消する。
 */
@Mixin(targets = "jp.kaiz.atsassistmod.ifttt.IFTTTContainer$That$ATSAssist$JavaScript", remap = false)
public abstract class ATSAssistJavaScriptUnlockMixin {

    @Shadow
    private transient ScriptEngine scriptEngine;

    @Shadow
    private boolean error;

    /**
     * リトライ間隔(tick)。20tick = 約1秒。
     * 値を大きくするほどチャット/コンソールへのエラースパムは減るが、
     * スクリプト修正が反映されるまでの体感速度は遅くなる。
     */
    private static final int CROSSTIE_RETRY_INTERVAL_TICKS = 20;

    @Inject(method = "doThat", at = @At("HEAD"))
    private void crosstie$unlockErrorDeadlock(TileEntityIFTTT tile, EntityTrainBase train, boolean first, CallbackInfo ci) {
        if (this.error && tile.getTick() % CROSSTIE_RETRY_INTERVAL_TICKS == 0) {
            this.error = false;
            this.scriptEngine = null;
        }
    }
}
