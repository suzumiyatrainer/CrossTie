package net.suzumiya.crosstie.accessors.rtm;

import jp.ngt.ngtlib.renderer.DisplayList;

/**
 * 分岐レール静止時の動的パーツ用 DisplayList キャッシュを {@code TileEntityLargeRailCore} に保持させるアクセサ。
 */
public interface ISwitchDynamicDisplayListHolder {
    DisplayList crosstie$getSwitchDynamicGLList();

    void crosstie$setSwitchDynamicGLList(DisplayList list);

    int crosstie$getSwitchDynamicStateKey();

    void crosstie$setSwitchDynamicStateKey(int key);

    void crosstie$deleteSwitchDynamicGLList();
}
