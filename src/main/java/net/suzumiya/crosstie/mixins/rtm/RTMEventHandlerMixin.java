package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationManager;
import jp.ngt.rtm.event.RTMEventHandler;
import jp.ngt.rtm.network.PacketFormation;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * {@link RTMEventHandler} に対する同期とクリーンアップの Mixin。
 *
 * <p>
 * Issue #87 の修正:
 * <ol>
 * <li>プレイヤーログイン時にサーバー側の全編成データを該当プレイヤーへ同期送信する。</li>
 * <li>クライアントがサーバー/ワールドから切断した際、クライアント側 FormationManager のキャッシュをクリアし、
 * 次回再入場時に前回のワールドの古い編成データが汚染・クラッシュを引き起こすのを防ぐ。</li>
 * </ol>
 */
@Mixin(value = RTMEventHandler.class, remap = false)
public abstract class RTMEventHandlerMixin {

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
}
