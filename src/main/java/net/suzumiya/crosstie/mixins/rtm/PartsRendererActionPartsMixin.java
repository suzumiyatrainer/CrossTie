package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.ActionParts;
import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * CrossTie: Angelica環境下でのActionParts登録復活、および視線フォーカス・距離制御Mixin。
 *
 * <ul>
 *   <li>Angelica環境下でKaizPatchXによってブロックされていたActionParts登録を有効化</li>
 *   <li>「マウスでそのオブジェクトを見ているときのみ操作可能」にするため、視線が外れた場合（hits <= 0）は
 *       右クリック中であっても保持せず即座に選択解除する（視線が合っている時のみ操作許可）</li>
 *   <li>手が届く範囲（乗車中または近距離）のオブジェクトのみ操作を許可し、遠距離からの誤操作を防止</li>
 * </ul>
 */
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererActionPartsMixin {

    @Shadow
    protected List<ActionParts> targetsList;

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
     * 視線判定と距離制御：
     * 視線がパーツから外れている場合（hits <= 0）、右クリック中であってもパーツ選択を保持せず即座にnullとする。
     * また、プレイヤーの手が届く距離（乗車中または約6m以内）でない場合も操作をブロックする。
     */
    @Inject(method = "selectHits", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$selectHitsStrict(Object t, int hits, CallbackInfoReturnable<ActionParts> cir) {
        // 視線が外れている場合は常にnull（視線が合っている時のみ操作可能）
        if (hits <= 0) {
            cir.setReturnValue(null);
            return;
        }

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
    }
}
