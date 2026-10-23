package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntityMachineBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetMachine;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * モデルJSONでスクリプト（serverScriptPath）が指定されていない TileEntityMachineBase の
 * updateEntity() 呼び出しをキャンセルし、サーバー側の Tick 負荷（MSPT）を最適化します。
 * <p>
 * ■ 動作仕様:
 * <ul>
 *   <li>TileEntityMachineBase 本体の updateEntity() は {@code ++this.tick;} と {@code executer.execScript(this);} のみを行う。</li>
 *   <li>無JSモデルでは RTM 側の callMethod() で {@code set.serverSE != null} により実行されないため、
 *       updateEntity() そのものを HEAD でスキップしても状態や動作に一切影響を与えない（完全非破壊）。</li>
 *   <li>踏切（TileEntityCrossingGate）や改札（TileEntityTurnstile）などのサブクラスで
 *       独自のアニメーションやタイマーを持つものは、サブクラスの処理（バー開閉、警報音等）は通常通り実行され、
 *       空の super.updateEntity() のみがスキップされるため安全に動作を維持する。</li>
 *   <li>モデル変更時（setModelName）および NBT 読み込み時にキャッシュフラグをリセットし、
 *       後からのモデル変更（無JS ⇄ 有JS）にも確実に追従する。</li>
 * </ul>
 */
@Mixin(value = TileEntityMachineBase.class, remap = false)
public abstract class TileEntityMachineUpdateOptimizationMixin {

    @Unique
    private byte crosstie$scriptState = 0; // 0: UNKNOWN, 1: NO_SCRIPT, 2: HAS_SCRIPT

    @Unique
    private boolean crosstie$hasServerScript() {
        if (this.crosstie$scriptState == 0) {
            TileEntityMachineBase self = (TileEntityMachineBase) (Object) this;
            ModelSetMachine set = self.getModelSet();
            if (set != null && !set.isDummy()) {
                this.crosstie$scriptState = (set.serverSE != null) ? (byte) 2 : (byte) 1;
            } else {
                return true; // モデル未解決時は安全のため実行を通す
            }
        }
        return this.crosstie$scriptState == 2;
    }

    @Inject(method = "setModelName", at = @At("HEAD"))
    private void crosstie$onSetModelName(String par1, CallbackInfo ci) {
        this.crosstie$scriptState = 0;
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$scriptState = 0;
    }

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$onUpdateEntity(CallbackInfo ci) {
        if (!this.crosstie$hasServerScript()) {
            ci.cancel();
        }
    }
}
