package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@SuppressWarnings("all")
@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailPartsRendererOptimizationMixin {

    /**
     * @author CrossTie
     * @reason Optimize hot loop: replace IntStream with basic for-loop and cache
     *         brightness queries for identical block coordinates.
     *         KaizPatchX 1.10.3 対応: チャンク境界をまたぐ SectionCore の未ロードチャンク fallback を統合。
     *
     * <p>最適化戦略:
     * <ol>
     *   <li>同一ブロック座標への連続クエリをキャッシュして重複呼び出しを削減（CrossTie 独自）</li>
     *   <li>{@code world.blockExists()} チェックで未ロードチャンクへのアクセスをスキップ（1.10.3 準拠）</li>
     *   <li>前方から前の有効値、後方から補完ループで穴埋め（1.10.3 準拠）</li>
     * </ol>
     */
    @Overwrite
    protected final int[] getRailBrightness(World world, int x, int y, int z, float[][] rp) {
        final int UNAVAILABLE = Integer.MIN_VALUE;
        int len = rp.length;
        int[] brightness = new int[len];
        if (len == 0) {
            return brightness;
        }

        // 未ロードチャンクの際の初期 fallback（CoreTileEntity の座標）
        int fallback = ((RailPartsRenderer) (Object) this).getBrightness(world, x, y, z);

        // キャッシュ用変数（同一座標の連続クエリを効率化）
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int lastBrightness = fallback;
        boolean lastLoaded = false;

        int previous = UNAVAILABLE;

        for (int i = 0; i < len; i++) {
            float[] pos = rp[i];
            int x0 = x + MathHelper.floor_double(pos[0]);
            int y0 = y + MathHelper.floor_double(pos[1]);
            int z0 = z + MathHelper.floor_double(pos[2]);

            if (x0 == lastX && y0 == lastY && z0 == lastZ) {
                // 同一座標キャッシュ: blockExists チェック結果も再利用
                if (lastLoaded) {
                    brightness[i] = lastBrightness;
                    previous = lastBrightness;
                    fallback = lastBrightness;
                } else {
                    brightness[i] = (previous != UNAVAILABLE) ? previous : UNAVAILABLE;
                }
            } else if (world.blockExists(x0, y0, z0)) {
                // 通常取得
                lastBrightness = ((RailPartsRenderer) (Object) this).getBrightness(world, x0, y0, z0);
                brightness[i] = lastBrightness;
                previous = lastBrightness;
                fallback = lastBrightness;
                lastLoaded = true;
            } else {
                // 未ロードチャンク: 前の有効値で前埋め（後方補完ループで穴埋め予定）
                brightness[i] = (previous != UNAVAILABLE) ? previous : UNAVAILABLE;
                lastLoaded = false;
            }
            lastX = x0;
            lastY = y0;
            lastZ = z0;
        }

        // 後方補完ループ: 前方から埋められなかった先頭部分を後ろから補完する
        int next = fallback;
        for (int i = brightness.length - 1; i >= 0; i--) {
            if (brightness[i] == UNAVAILABLE) {
                brightness[i] = next;
            } else {
                next = brightness[i];
            }
        }

        return brightness;
    }
}



// Minimal stub to satisfy compile-time reference to the real target type.
// The real RailPartsRenderer comes from the target mod at runtime; this
// placeholder only exists to allow compilation in this project setup.
abstract class RailPartsRenderer {
    // Signature matches the method used by the mixin. Implementation is unused
    // at compile-time and will be shadowed by the runtime class.
    protected int getBrightness(World world, int x, int y, int z) {
        return 0;
    }
}
