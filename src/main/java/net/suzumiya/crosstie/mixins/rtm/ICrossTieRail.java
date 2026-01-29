package net.suzumiya.crosstie.mixins.rtm;

/**
 * TileEntityLargeRailCoreにキャッシュ機能を追加するインターフェース
 */
public interface ICrossTieRail {
    /**
     * このレールがhi03ExpressRailwayモデルかどうかを返します（キャッシュ済み）
     * 
     * @return 0:未判定, 1:hi03, 2:非hi03
     */
    byte crosstie$getHi03Cache();

    /**
     * hi03ExpressRailwayモデルかどうかを設定します
     * 
     * @param status 1:hi03, 2:非hi03
     */
    void crosstie$setHi03Cache(byte status);
}
