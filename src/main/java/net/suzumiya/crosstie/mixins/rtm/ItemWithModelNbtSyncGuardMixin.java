package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.item.ItemWithModel;
import jp.ngt.rtm.modelpack.state.ResourceState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CrossTie: ItemWithModel におけるピックブロック時の無用なNBTパケット送信と selectedPlayer 残存防止 Mixin
 *
 * <h3>問題の背景</h3>
 * <p>ItemWithModel（シングルトン Item）は selectedPlayer フィールドを持ち、モデル選択GUIを開いた際にセットされる。
 * しかしGUI閉鎖後もこの参照が null にクリアされないため、getPickBlock() 等で setModelState() が呼ばれた際にも
 * selectedPlayer != null と判定され、ピックブロック中に意図しない PacketNBT がサーバーへ送信されていた。</p>
 *
 * <h3>修正内容</h3>
 * <ol>
 *   <li>GUI閉鎖時 (closeGui) に selectedPlayer および selectedItem を null にクリアする。</li>
 *   <li>setModelState 実行時、closeGui 経由以外の呼び出し（getPickBlock等）では一時的に selectedPlayer を抑止し、
 *       ピックブロック中のパケット自動送信をガードする。</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = ItemWithModel.class, remap = false)
public abstract class ItemWithModelNbtSyncGuardMixin {

    @Shadow
    private EntityPlayer selectedPlayer;

    @Shadow
    private ItemStack selectedItem;

    @Unique
    private boolean crosstie$isExplicitGuiClose = false;

    @Unique
    private EntityPlayer crosstie$savedPlayer = null;

    @Inject(
        method = "closeGui",
        at = @At("HEAD"),
        remap = false
    )
    private void crosstie$onCloseGuiStart(String par1, ResourceState par2, CallbackInfoReturnable<Boolean> cir) {
        this.crosstie$isExplicitGuiClose = true;
    }

    @Inject(
        method = "closeGui",
        at = @At("RETURN"),
        remap = false
    )
    private void crosstie$onCloseGuiEnd(String par1, ResourceState par2, CallbackInfoReturnable<Boolean> cir) {
        this.crosstie$isExplicitGuiClose = false;
        this.selectedPlayer = null;
        this.selectedItem = null;
    }

    @Inject(
        method = "setModelState",
        at = @At("HEAD"),
        remap = false
    )
    private void crosstie$beforeSetModelState(ItemStack itemStack, ResourceState state, CallbackInfo ci) {
        if (!this.crosstie$isExplicitGuiClose) {
            this.crosstie$savedPlayer = this.selectedPlayer;
            this.selectedPlayer = null;
        }
    }

    @Inject(
        method = "setModelState",
        at = @At("RETURN"),
        remap = false
    )
    private void crosstie$afterSetModelState(ItemStack itemStack, ResourceState state, CallbackInfo ci) {
        if (!this.crosstie$isExplicitGuiClose && this.crosstie$savedPlayer != null) {
            this.selectedPlayer = this.crosstie$savedPlayer;
            this.crosstie$savedPlayer = null;
        }
    }
}
