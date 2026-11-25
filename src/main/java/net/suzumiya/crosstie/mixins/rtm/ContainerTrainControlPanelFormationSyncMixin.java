package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.gui.ContainerTrainControlPanel;
import jp.ngt.rtm.gui.TabTrainControlPanel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.suzumiya.crosstie.utils.FormationSyncHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 車両管理GUI ({@link ContainerTrainControlPanel}) を開いた時、および編成タブに切り替えた時に、
 * サーバー側から最新の {@code PacketFormation} をクライアントへ強制送信する Mixin。
 *
 * <p>
 * これにより、チャンク読込順・トラッキング開始タイミング・パケット順序ズレ等でクライアント側の
 * {@code Formation} が欠損（1両表示など）していても、GUIオープン/タブ切替直後に最新の連結状態へ同期される。
 * </p>
 */
@Mixin(value = ContainerTrainControlPanel.class, remap = false)
public abstract class ContainerTrainControlPanelFormationSyncMixin {

    @Shadow
    public EntityTrainBase train;

    @Shadow
    public EntityPlayer player;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void crosstie$syncFormationOnOpen(EntityTrainBase par1, EntityPlayer par2, CallbackInfo ci) {
        if (par1 != null && par2 instanceof EntityPlayerMP && par2.worldObj != null && !par2.worldObj.isRemote) {
            FormationSyncHelper.sendFormationToPlayer(par1, (EntityPlayerMP) par2, true);
        }
    }

    @Inject(method = "setCurrentTab", at = @At("RETURN"), require = 0)
    private void crosstie$syncFormationOnTabSwitch(int tabIndex, CallbackInfo ci) {
        if (this.train != null && this.player instanceof EntityPlayerMP
                && this.player.worldObj != null && !this.player.worldObj.isRemote) {
            if (tabIndex == TabTrainControlPanel.TAB_Formation.getTabIndex()) {
                FormationSyncHelper.sendFormationToPlayer(this.train, (EntityPlayerMP) this.player, true);
            }
        }
    }
}
