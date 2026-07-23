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
 * CrossTie: ItemWithModel におけるモデル選択時の NPE ガードおよびピックブロック時の無用なNBTパケット送信防止 Mixin
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

    /**
     * itemStack が null で getModelState() が呼ばれた際の NPE 完全防止ガード
     */
    @Inject(
        method = "getModelState(Lnet/minecraft/item/ItemStack;)Ljp/ngt/rtm/modelpack/state/ResourceState;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void crosstie$guardNullItemStack(ItemStack itemStack, CallbackInfoReturnable<ResourceState> cir) {
        if (itemStack == null) {
            ItemWithModel self = (ItemWithModel) (Object) this;
            if (this.selectedItem != null && this.selectedItem != itemStack) {
                cir.setReturnValue(self.getModelState(this.selectedItem));
            } else {
                cir.setReturnValue(new ResourceState(self));
            }
        }
    }

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
