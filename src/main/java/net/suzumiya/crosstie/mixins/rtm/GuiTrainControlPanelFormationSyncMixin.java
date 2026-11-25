package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.gui.GuiTrainControlPanel;
import jp.ngt.rtm.gui.TabTrainControlPanel;
import net.suzumiya.crosstie.utils.FormationSyncHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link GuiTrainControlPanel} の編成タブ表示中に、クライアント側の {@link Formation} が
 * サーバーからの {@code PacketFormation} 受信や遅延エンティティ解決 (Self-Healing) によって
 * 更新された場合、編成ボタン一覧をリアルタイムに再構築するクライアント専用 Mixin。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = GuiTrainControlPanel.class, remap = false)
public abstract class GuiTrainControlPanelFormationSyncMixin {

    @Shadow
    private int selectedTabIndex;

    @Shadow
    protected EntityTrainBase train;

    @Shadow
    private void setCurrentTab(TabTrainControlPanel tab) {
    }

    @Unique
    private boolean crosstie$suppressTabPacket;

    @Unique
    private Formation crosstie$lastFormation;

    @Unique
    private long crosstie$lastFormationId = Long.MIN_VALUE;

    @Unique
    private int crosstie$lastFormationSize = -1;

    @Unique
    private int crosstie$lastResolvedCount = -1;

    @Unique
    private long crosstie$lastSyncVersion = -1L;

    @Inject(method = "sendTabPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$cancelRedundantTabPacket(int tabIndex, CallbackInfo ci) {
        if (this.crosstie$suppressTabPacket) {
            ci.cancel();
        }
    }

    @Inject(method = "setCurrentTab", at = @At("RETURN"), require = 0)
    private void crosstie$recordFormationSnapshot(TabTrainControlPanel tab, CallbackInfo ci) {
        if (tab == TabTrainControlPanel.TAB_Formation) {
            crosstie$captureSnapshot(this.train != null ? this.train.getFormation() : null);
        }
    }

    @Inject(method = { "updateScreen", "func_73876_c" }, at = @At("RETURN"), require = 0)
    private void crosstie$refreshFormationTabIfUpdated(CallbackInfo ci) {
        if (this.train == null || this.selectedTabIndex != TabTrainControlPanel.TAB_Formation.getTabIndex()) {
            return;
        }

        // getFormation() によりクライアント側の最新編成・未解決エントリの遅延解決を促す
        Formation current = this.train.getFormation();
        if (current != null) {
            FormationSyncHelper.resolveAllPendingForFormation(current);
        }

        long syncVersion = FormationSyncHelper.getClientSyncVersion();
        long currentId = current != null ? current.id : Long.MIN_VALUE;
        int currentSize = current != null ? current.size() : -1;
        int resolvedCount = crosstie$countResolvedEntries(current);

        if (current != this.crosstie$lastFormation
                || currentId != this.crosstie$lastFormationId
                || currentSize != this.crosstie$lastFormationSize
                || resolvedCount != this.crosstie$lastResolvedCount
                || syncVersion != this.crosstie$lastSyncVersion) {
            this.crosstie$suppressTabPacket = true;
            try {
                this.setCurrentTab(TabTrainControlPanel.TAB_Formation);
            } finally {
                this.crosstie$suppressTabPacket = false;
            }
            crosstie$captureSnapshot(this.train.getFormation());
        }
    }

    @Unique
    private void crosstie$captureSnapshot(Formation formation) {
        this.crosstie$lastFormation = formation;
        this.crosstie$lastFormationId = formation != null ? formation.id : Long.MIN_VALUE;
        this.crosstie$lastFormationSize = formation != null ? formation.size() : -1;
        this.crosstie$lastResolvedCount = crosstie$countResolvedEntries(formation);
        this.crosstie$lastSyncVersion = FormationSyncHelper.getClientSyncVersion();
    }

    @Unique
    private static int crosstie$countResolvedEntries(Formation formation) {
        if (formation == null || formation.entries == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < formation.entries.length; i++) {
            FormationEntry entry = formation.entries[i];
            if (entry != null && entry.train != null && !entry.train.isDead) {
                count++;
            }
        }
        return count;
    }
}
