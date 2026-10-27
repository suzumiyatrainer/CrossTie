package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.EntityInstalledObject;
import jp.ngt.rtm.modelpack.modelset.ModelSetMachine;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link jp.ngt.rtm.entity.RenderEntityInstalledObject#doRender} への Mixin。
 *
 * <p>
 * RTM の {@code EntityInstalledObject}（車止め・ATC 等の地上子エンティティ）は、
 * ワールドロード直後・遠距離移動後・リログ後などモデルパックが非同期ロード中の間、
 * {@link EntityInstalledObject#getModelSet()} が <em>ダミー</em> の
 * {@code ModelSetMachineClient} を返し続ける。
 *
 * <p>
 * ダミー {@code ModelSetMachineClient} はコンストラクタ内で {@code ModelMachine_Point01.mqo}
 * （踏切ポールの形状）をハードコードしており、本来のモデルパックが解決されるまでの間、 黒い直方体（ポール）が表示されてしまう。
 *
 * <p>
 * 本 Mixin は {@code doRender()} の先頭で {@code getModelSet().isDummy()} を確認し、
 * ダミーの場合は描画をスキップして誤表示を防ぐ。 モデルが正常に解決された次フレームからは通常描画に戻る。
 */
@Mixin(targets = "jp.ngt.rtm.entity.RenderEntityInstalledObject", remap = false)
public abstract class RenderEntityInstalledObjectDummySkipMixin {

    @Inject(method = "doRender", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$skipDummyModel(Entity entity, double par2, double par4, double par6, float par8, float par9,
            CallbackInfo ci) {
        if (!(entity instanceof EntityInstalledObject)) {
            return;
        }
        ModelSetMachine modelSet = ((EntityInstalledObject) entity).getModelSet();
        if (modelSet != null && modelSet.isDummy()) {
            ci.cancel();
        }
    }
}
