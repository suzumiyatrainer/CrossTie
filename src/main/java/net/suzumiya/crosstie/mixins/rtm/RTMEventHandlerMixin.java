package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationManager;
import jp.ngt.rtm.event.RTMEventHandler;
import jp.ngt.rtm.network.PacketFormation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.suzumiya.crosstie.utils.FormationSyncHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * {@link RTMEventHandler} に対する編成同期とワールド再入場時クリーンアップの Mixin。
 */
@Mixin(value = RTMEventHandler.class, remap = false)
public abstract class RTMEventHandlerMixin {

    /**
     * クライアントがサーバー/ワールドへ接続した際、前回のワールドで残ったクライアント側編成キャッシュと
     * 未解決エントリ予約をクリアする。
     */
    @Inject(method = "connectedToServer", at = @At("HEAD"), require = 0)
    private void crosstie$clearClientFormationsOnConnect(ClientConnectedToServerEvent event, CallbackInfo ci) {
        try {
            FormationSyncHelper.clearClientPending();
            if (!NGTUtil.isServer()) {
                FormationManager fm = FormationManager.getInstance();
                if (fm != null) {
                    fm.clearFormations();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ワールドがアンロードされた際、クライアント側またはディメンション0アンロード時の古い編成キャッシュをクリアする。
     */
    @Inject(method = "onUnloadWorld", at = @At("RETURN"), require = 0)
    private void crosstie$clearFormationsOnWorldUnload(WorldEvent.Unload event, CallbackInfo ci) {
        if (event.world == null) {
            return;
        }
        try {
            if (event.world.isRemote) {
                FormationSyncHelper.clearClientPending();
                FormationManager fm = FormationManager.getInstance();
                if (fm != null) {
                    fm.clearFormations();
                }
            } else if (event.world.provider != null && event.world.provider.dimensionId == 0) {
                FormationSyncHelper.clearServerDebounce();
                FormationManager fm = FormationManager.getInstance();
                if (fm != null) {
                    fm.clearFormations();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * サーバー側でディメンション0がロードされる際、前回のワールドの編成マップ残骸をクリアしてから
     * loadData(world) による編成データのロードを実行させる。
     */
    @Inject(method = "onLoadWorld", at = @At("HEAD"), require = 0)
    private void crosstie$clearServerFormationsBeforeLoad(WorldEvent.Load event, CallbackInfo ci) {
        if (event.world != null && !event.world.isRemote
                && event.world.provider != null && event.world.provider.dimensionId == 0) {
            try {
                FormationSyncHelper.clearServerDebounce();
                FormationManager fm = FormationManager.getInstance();
                if (fm != null) {
                    fm.clearFormations();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * プレイヤーがログインした際、サーバー側に存在する全編成データをパケットで送信して同期する。
     */
    @Inject(method = "onPlayerLoggedIn", at = @At("RETURN"), require = 0)
    private void crosstie$syncFormationsOnLogin(PlayerLoggedInEvent event, CallbackInfo ci) {
        if (!NGTUtil.isServer() || !(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        FormationManager fm = FormationManager.getInstance();
        if (fm == null) {
            return;
        }

        Map<Long, Formation> formations = fm.getFormations();
        if (formations == null || formations.isEmpty()) {
            return;
        }

        for (Formation formation : formations.values()) {
            if (formation != null && formation.entries != null && formation.entries.length > 0) {
                try {
                    RTMCore.NETWORK_WRAPPER.sendTo(new PacketFormation(formation), player);
                } catch (Throwable t) {
                    // 同期失敗時もプレイヤーのログイン自体は阻害しない
                }
            }
        }
    }

    /**
     * プレイヤーが列車エンティティのトラッキングを開始した際（遠方からの接近、テレポート、
     * ディメンション移動、チャンクロード後など）、対象列車の最新 {@link Formation} を
     * {@link PacketFormation} で送信してクライアント側の編成欠損を防ぐ。
     *
     * <p>{@code RTMEventHandler} は {@code MinecraftForge.EVENT_BUS} に登録されているため、
     * このイベントハンドラは RTM 導入時に自動で登録・実行される。</p>
     */
    @SubscribeEvent
    public void crosstie$onStartTrackingTrain(PlayerEvent.StartTracking event) {
        if (event == null || !(event.target instanceof EntityTrainBase) || !(event.entityPlayer instanceof EntityPlayerMP)) {
            return;
        }
        EntityTrainBase train = (EntityTrainBase) event.target;
        if (train.worldObj == null || train.worldObj.isRemote) {
            return;
        }
        FormationSyncHelper.sendFormationToPlayer(train, (EntityPlayerMP) event.entityPlayer, false);
    }
}
