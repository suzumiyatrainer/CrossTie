package net.suzumiya.crosstie.util;

import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;

/**
 * フラストラム（視錐台）計算の共有キャッシュユーティリティ。
 *
 * <p>
 * 同一フレーム内で複数のMixinが独立してフラストラム計算を行うコストを削減するため、
 * {@code Minecraft.getSystemTime()} が変化しない限り前回の {@link Frustrum} インスタンスを
 * 再利用する。
 *
 * <p>
 * クライアントのレンダースレッドからのみ使用する。スレッドセーフではない。
 */
public final class CrossTieFrustumCache {

    private static final ICamera FRUSTUM = new Frustrum();
    private static long lastFrame = -1L;

    private CrossTieFrustumCache() {}

    /**
     * 現在フレームのフラストラムを返す。
     * フレームが変わった場合のみ {@code setPosition} を呼び直す。
     *
     * @param renderView レンダービュー基準となるエンティティ（通常はプレイヤー）
     * @return フラストラム {@link ICamera} インスタンス
     */
    public static ICamera get(Entity renderView) {
        long now = net.minecraft.client.Minecraft.getSystemTime();
        if (now != lastFrame) {
            FRUSTUM.setPosition(renderView.posX, renderView.posY, renderView.posZ);
            lastFrame = now;
        }
        return FRUSTUM;
    }
}
