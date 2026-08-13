package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import net.suzumiya.crosstie.utils.CrossTiePartsRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CrossTie: 電車のドア開閉時の固定ドア二重描画（残像）現象を根治するMixin。
 *
 * <p>【背景と原因】
 * 一部の電車モデルパックでは、スライド移動する動くドアパーツ（doorFL, doorFR等）とは別に、
 * 車体メッシュ（body/exterior）や固定パーツ群（doorLa_f_L, doorLa_f_R, door_close等）の中に
 * 「閉じた状態の固定ドア」が含まれて登録されています。
 * ドアが0.001でも開き始めると（doorMove > 0）、動くドアパーツは正常にスライド移動しますが、
 * 固定ドアパーツが元位置に描画され続けるため、閉じたドアの残像が重複表示されてしまいます。
 *
 * <p>【対策】
 * 現在描画中の車両のドアが動作中（doorMoveL > 0 || doorMoveR > 0）である場合、
 * 動くメインドア（doorFL, doorFR, doorBL, doorBR等）以外の固定ドアパーツの描画をキャンセルし、
 * 動いているドアのみを表示するように制御します。
 */
@Mixin(value = Parts.class, remap = false)
public abstract class PartsDoorGhostFixMixin {

    @Shadow
    public String[] objNames;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void crosstie$suppressStaticDoorGhostOnMove(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        Object vehicleObj = CrossTiePartsRenderContext.getCurrentVehicle();
        if (!(vehicleObj instanceof EntityVehicleBase)) {
            return;
        }

        EntityVehicleBase<?> vehicle = (EntityVehicleBase<?>) vehicleObj;
        boolean isDoorMovingL = vehicle.doorMoveL > 0;
        boolean isDoorMovingR = vehicle.doorMoveR > 0;

        // ドアが完全に閉じている（動いていない）場合は通常通り描画
        if (!isDoorMovingL && !isDoorMovingR) {
            return;
        }

        if (this.objNames == null || this.objNames.length == 0) {
            return;
        }

        // 動いているアニメーションドアパーツ自体は消さずに通過させる
        if (crosstie$isAnimatedDoor(this.objNames)) {
            return;
        }

        // ドア動作中、アニメーションドア以外の固定ドアパーツ（doorLa_f_L, door_close, door_static等）は描画キャンセル
        if (crosstie$containsStaticDoorName(this.objNames)) {
            ci.cancel();
        }
    }

    /**
     * アニメーションして実際に動くドアパーツかどうかを判定する。
     */
    private static boolean crosstie$isAnimatedDoor(String[] names) {
        for (String name : names) {
            if (name == null) continue;
            String lower = name.toLowerCase().trim();
            // 一般的な動くドアパーツ名パターン
            if (lower.equals("doorfl") || lower.equals("doorfr") || lower.equals("doorbl") || lower.equals("doorbr") ||
                lower.equals("doorfl1") || lower.equals("doorfr1") || lower.equals("doorbl1") || lower.equals("doorbr1") ||
                lower.equals("door_fl") || lower.equals("door_fr") || lower.equals("door_bl") || lower.equals("door_br") ||
                lower.equals("doorl") || lower.equals("doorr") || lower.equals("door_l") || lower.equals("door_r") ||
                lower.equals("door1") || lower.equals("door2") || lower.equals("door3") || lower.equals("door4") ||
                lower.startsWith("doorfl") || lower.startsWith("doorfr") || lower.startsWith("doorbl") || lower.startsWith("doorbr")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 重複描画（残像）の原因となる固定の閉じたドアパーツかどうかを判定する。
     */
    private static boolean crosstie$containsStaticDoorName(String[] names) {
        for (String name : names) {
            if (name == null) continue;
            String lower = name.toLowerCase().trim();
            // 固定ドア・残像の原因となるパーツ名パターン
            if (lower.contains("doorla_f") || lower.contains("door_close") || lower.contains("doorclose") ||
                lower.contains("door_static") || lower.contains("doorstatic") || lower.contains("door_base") ||
                lower.contains("door_fix") || lower.contains("doorfix") || lower.contains("door_c") ||
                lower.contains("door_bg") || lower.contains("door_def") || lower.contains("door_closed") ||
                lower.equals("door_0") || lower.equals("door0") || lower.contains("door_stop") || lower.contains("doorstop")) {
                return true;
            }
        }
        return false;
    }
}
