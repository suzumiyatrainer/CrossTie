package net.suzumiya.crosstie.accessors.rtm;

/**
 * Phase 5-3: {@code TileEntityElectricalWiring} に架線 1 本ごとの単一 VBO DisplayList キャッシュを保持させるアクセサ。
 */
public interface IWireDisplayListHolder {

    Object crosstie$getWireCache();

    void crosstie$setWireCache(Object cache);

    void crosstie$releaseWireCache();
}
