package net.suzumiya.crosstie.accessors.ngtlib;

/**
 * {@link jp.ngt.ngtlib.block.TileEntityPlaceable} 系の有限 AABB キャッシュの dirty 状態管理アクセサ。
 */
public interface IAabbDirtyHolder {
    boolean crosstie$isAabbDirty();

    void crosstie$markAabbDirty();

    void crosstie$clearAabbDirty();
}
