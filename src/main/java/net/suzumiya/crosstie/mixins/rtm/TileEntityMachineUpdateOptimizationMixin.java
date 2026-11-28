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
 * モデルJSONでサーバー側スクリプト（serverScriptPath）が指定されていない TileEntityMachineBase の
 * サーバー側 updateEntity() 呼び出しをキャンセルし、MSPT を最適化します。
 *
 * <p>クライアント側では {@code ++this.tick;} が描画 JS のアニメーションに使用されるため、
 * {@code !worldObj.isRemote}（サーバー側）でのみキャンセルを行います。</p>
 */
@Mixin(TileEntityMachineBase.class)
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

    @Inject(method = "setModelName", at = @At("HEAD"), remap = false)
    private void crosstie$onSetModelName(String par1, CallbackInfo ci) {
        this.crosstie$scriptState = 0;
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void crosstie$onReadFromNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.crosstie$scriptState = 0;
    }

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$onUpdateEntity(CallbackInfo ci) {
        TileEntityMachineBase self = (TileEntityMachineBase) (Object) this;
        if (self.getWorldObj() == null || self.getWorldObj().isRemote) {
            return; // クライアント側の tick アニメーションを保護
        }
        if (!this.crosstie$hasServerScript()) {
            ci.cancel();
        }
    }
}
