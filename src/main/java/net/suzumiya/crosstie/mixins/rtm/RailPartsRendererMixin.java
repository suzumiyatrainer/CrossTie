package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.render.RailPartsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;

/**
 * S-1: RailPartsRenderer.createMatrix() での FloatBuffer 毎フレームアロケーションを排除する Mixin。
 *
 * <p>元の実装では毎フレーム・毎レールに対して {@code FloatBuffer.allocate(rp.length << 4)} が呼ばれ、
 * GC 圧力と JIT 最適化の妨げになっていた。
 * ThreadLocal で事前確保したバッファを再利用することでアロケーションをゼロにする。</p>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailPartsRendererMixin {

    /**
     * スレッドローカルな FloatBuffer プール。
     * レール1本あたり最大 1024 セグメント × 16 float = 16384 エントリを想定して確保。
     * 足りない場合は動的に拡張する。
     */
    @Unique
    private static final ThreadLocal<FloatBuffer> CROSSTIE_MATRIX_BUF =
        ThreadLocal.withInitial(() -> FloatBuffer.allocate(1024 << 4));

    /**
     * {@code FloatBuffer.allocate(int)} 呼び出しをリダイレクトし、
     * プールされたバッファ（必要に応じて拡張）を返す。
     */
    @Redirect(
        method = "renderStaticPartsGeometry",
        at = @At(
            value = "INVOKE",
            target = "Ljava/nio/FloatBuffer;allocate(I)Ljava/nio/FloatBuffer;",
            remap = false
        ),
        require = 0
    )
    private static FloatBuffer crosstie$reuseFloatBuffer(int capacity) {
        FloatBuffer buf = CROSSTIE_MATRIX_BUF.get();
        if (buf.capacity() < capacity) {
            // 必要なサイズに合わせて新しいバッファに置き換える（頻度は低い）
            buf = FloatBuffer.allocate(capacity);
            CROSSTIE_MATRIX_BUF.set(buf);
        }
        buf.clear(); // limit = capacity, position = 0 にリセット
        return buf;
    }
}
