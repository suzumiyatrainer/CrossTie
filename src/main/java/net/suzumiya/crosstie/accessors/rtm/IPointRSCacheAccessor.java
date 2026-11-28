package net.suzumiya.crosstie.accessors.rtm;

import net.minecraft.world.World;

/**
 * {@link jp.ngt.rtm.rail.util.Point} のキャッシュ済み RS 入力状態へ外部（WebCTC フック等）から
 * 重複ワールドアクセス無しで参照するためのアクセサ。
 */
public interface IPointRSCacheAccessor {
    boolean crosstie$getCachedRSInput(World world);
}
