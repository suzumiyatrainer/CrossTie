package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.NGTRenderHelper;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.utils.CrossTiePartsRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

/**
 * CrossTie: GroupObject レベルでの固定ドア重複（残像）フィルタリング Mixin。
 *
 * <p>
 * body や exterior 等の Parts 内に固定ドアメッシュ（doorLa_f_L, door_close等）が
 * 混入している場合でも、ドア動作中にその GroupObject の描画をピンポイントでスキップします。
 */
@Mixin(value = NGTRenderHelper.class, remap = false)
public abstract class NGTRenderHelperDoorFilterMixin {

    @Redirect(method = "renderCustomModelEveryParts(Ljp/ngt/ngtlib/renderer/model/IModelNGT;BZZI[Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z", remap = false), remap = false)
    private static boolean crosstie$filterStaticDoorGroup(Predicate<GroupObject> predicate, Object obj) {
        boolean originalResult = predicate.test((GroupObject) obj);
        if (!originalResult) {
            return false;
        }

        Object vehicleObj = CrossTiePartsRenderContext.getCurrentVehicle();
        if (!(vehicleObj instanceof EntityVehicleBase)) {
            return true;
        }

        EntityVehicleBase<?> vehicle = (EntityVehicleBase<?>) vehicleObj;
        boolean isDoorMovingL = vehicle.doorMoveL > 0;
        boolean isDoorMovingR = vehicle.doorMoveR > 0;

        if (!isDoorMovingL && !isDoorMovingR) {
            return true;
        }

        GroupObject group = (GroupObject) obj;
        if (group == null || group.name == null) {
            return true;
        }

        String name = group.name.toLowerCase().trim();

        // アニメーションドアは通常描画
        if (name.equals("doorfl") || name.equals("doorfr") || name.equals("doorbl") || name.equals("doorbr")
                || name.equals("doorfl1") || name.equals("doorfr1") || name.equals("doorbl1") || name.equals("doorbr1")
                || name.equals("door_fl") || name.equals("door_fr") || name.equals("door_bl") || name.equals("door_br")
                || name.equals("doorl") || name.equals("doorr") || name.equals("door_l") || name.equals("door_r")
                || name.equals("door1") || name.equals("door2") || name.equals("door3") || name.equals("door4")
                || name.startsWith("doorfl") || name.startsWith("doorfr") || name.startsWith("doorbl")
                || name.startsWith("doorbr")) {
            return true;
        }

        // ドア動作中、固定ドアグループ（doorLa_f_L, door_close等）は非表示
        if (name.contains("doorla_f") || name.contains("door_close") || name.contains("doorclose")
                || name.contains("door_static") || name.contains("doorstatic") || name.contains("door_base")
                || name.contains("door_fix") || name.contains("doorfix") || name.contains("door_c")
                || name.contains("door_bg") || name.contains("door_def") || name.contains("door_closed")
                || name.equals("door_0") || name.equals("door0") || name.contains("door_stop")
                || name.contains("doorstop")) {
            return false;
        }

        return true;
    }
}
