package net.suzumiya.crosstie.accessors.rtm;

/**
 * CrossTie: EntityVehicleBase 描画コンテキストフラグアクセサ
 *
 * EntityVehicleBaseModelSetGuardMixin が注入した
 * crosstie$isInRenderContext フラグを
 * 外部Mixin（RenderVehicleBaseContextMixin）から操作するためのインターフェース。
 */
public interface IEntityVehicleBaseRenderContextAccessor {
    boolean crosstie$isInRenderContext();
    void crosstie$setInRenderContext(boolean value);
}