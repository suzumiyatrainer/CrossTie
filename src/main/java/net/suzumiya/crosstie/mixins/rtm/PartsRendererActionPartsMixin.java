package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.ActionParts;
import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * CrossTie: Angelica環境下でのActionParts登録復活、および視線フォーカス・ドラッグ保持・距離制御Mixin。
 *
 * <ul>
 *   <li>Angelica環境下でKaizPatchXによってブロックされていたActionParts登録を有効化</li>
 *   <li>ドラッグ操作（マスコン/ブレーキ等）の保持：右クリック長押し中は視線が一時的に外れても選択を保持</li>
 *   <li>新規クリック/ドラッグ開始時：視線が合っている（hits > 0）時のみ操作を許可し、見ていないパーツの誤操作を防止</li>
 *   <li>手が届く範囲（乗車中または近距離）のオブジェクトのみ操作を許可</li>
 * </ul>
 */
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererActionPartsMixin {

    @Shadow
    protected List<ActionParts> targetsList;

    @Shadow
    protected Map<?, ActionParts> hittedParts;

    @Inject(method = "registerParts", at = @At("RETURN"), remap = false)
    private void crosstie$registerActionPartsInAngelica(Parts par1, CallbackInfoReturnable<Parts> cir) {
        if (par1 instanceof ActionParts) {
            ActionParts actionParts = (ActionParts) par1;
            if (!this.targetsList.contains(actionParts)) {
                actionParts.id = this.targetsList.size() + 1;
                this.targetsList.add(actionParts);
            }
        }
    }

    /**
     * 視線判定・ドラッグ保持・距離制御：
     * 1. 乗車中または至近距離（約7m以内）のみ操作許可
     * 2. ドラッグ中（右クリック長押し中かつ既に掴んでいるパーツがある）：
     *    マウスドラッグにより視線が外れても、右クリックを離すまで操作中パーツを保持
     * 3. 新規操作時（右クリックしていない、またはパーツを掴んでいない）：
     *    視線が合っていない（hits <= 0）場合は null とし、見ていないオブジェクトを操作させない
     */
    @Inject(method = "selectHits", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$selectHitsStrict(Object t, int hits, CallbackInfoReturnable<ActionParts> cir) {
        // プレイヤーと対象エンティティの距離チェック
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null && t instanceof Entity) {
            Entity targetEntity = (Entity) t;
            boolean isRiding = (mc.thePlayer.ridingEntity == targetEntity);
            if (!isRiding && mc.thePlayer.getDistanceToEntity(targetEntity) > 7.0F) {
                cir.setReturnValue(null);
                return;
            }
        }

        // ドラッグ操作中のパーツ保持：
        // 既にパーツを掴んで右クリック長押し中の場合は、ドラッグで視線が外れても保持する
        if (Mouse.isButtonDown(1) && this.hittedParts != null) {
            ActionParts currentHitted = this.hittedParts.get(t);
            if (currentHitted != null) {
                cir.setReturnValue(currentHitted);
                return;
            }
        }

        // 新規操作時：視線が合っていない場合は一切選択させない
        if (hits <= 0) {
            cir.setReturnValue(null);
            return;
        }
    }
}
