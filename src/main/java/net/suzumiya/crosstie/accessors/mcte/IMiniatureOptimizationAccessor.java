package net.suzumiya.crosstie.accessors.mcte;

/**
 * Phase 5-1: {@code TileEntityMiniature} の空 pass 判定キャッシュ・pass 別 dirty 管理アクセサ。
 */
public interface IMiniatureOptimizationAccessor {

    boolean crosstie$hasPassContent(int pass);

    boolean crosstie$hasBlockContent(int pass);

    void crosstie$invalidateMiniatureCache();

    boolean crosstie$isPassDirty(int pass);

    void crosstie$markAllPassesDirty();

    void crosstie$clearPassDirty(int pass);
}
