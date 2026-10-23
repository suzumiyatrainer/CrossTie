package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.RTMBlock;
import jp.ngt.rtm.electric.TileEntitySignal;
import jp.ngt.rtm.modelpack.modelset.ModelSetSignal;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * モデルJSONでスクリプト（serverScriptPath）が指定されていない TileEntitySignal の
 * サーバー側 updateEntity() 呼び出しをキャンセルし、サーバー側の Tick 負荷（MSPT）を最適化します。
 * <p>
 * ■ 動作仕様:
 * <ul>
 *   <li>TileEntitySignal のサーバー側 updateEntity() は {@code executer.execScript(this);} と
 *       初回設置時の架線柱（origTileEntity）補完処理のみを行う。</li>
 *   <li>架線柱一体型信号機の場合、初回Tickでの origTileEntity 生成が完了するまでは通常通り実行し、
 *       初期化完了後に無JSモデルであればサーバー側 updateEntity() を即座にキャンセルする。</li>
 *   <li>クライアント側では信号の点滅アニメーション（BasicSignalPartsRenderer）に {@code this.tick} が
 *       使用されるため、クライアント側Tickは維持して視覚効果を完全保持する。</li>
 * </ul>
 */
@Mixin(value = TileEntitySignal.class, remap = false)
public abstract class TileEntitySignalUpdateOptimizationMixin {

    @Shadow
    private Block renderBlock;

    @Shadow
    private TileEntity origTileEntity;

    @Unique
    private byte crosstie$scriptState = 0; // 0: UNKNOWN, 1: NO_SCRIPT, 2: HAS_SCRIPT

    @Unique
    private boolean crosstie$hasServerScript() {
        if (this.crosstie$scriptState == 0) {
            TileEntitySignal self = (TileEntitySignal) (Object) this;
            ModelSetSignal set = self.getModelSet();
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
        TileEntitySignal self = (TileEntitySignal) (Object) this;
        if (self.getWorldObj() != null && !self.getWorldObj().isRemote) {
            // 架線柱一体型信号の初期化完了を確認（未生成の間は初回Tickを実行させる）
            if (this.origTileEntity != null || this.renderBlock != RTMBlock.linePole) {
                if (!this.crosstie$hasServerScript()) {
                    ci.cancel();
                }
            }
        }
    }
}
