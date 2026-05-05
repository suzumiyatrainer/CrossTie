package net.suzumiya.crosstie.mixins.signal;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.util.CrossTieDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SignalControllerMod の TileEntitySignalController が毎 tick 行う
 * {@code world.getTileEntity(x, y, z)} の呼び出しを、弱参照キャッシュで削減する。
 *
 * <p>audit §4.3 / §9 P1:
 * "nextSignal / displayPos の TileEntitySignal 参照を weak/pos cache し、
 *  chunk unload/block change で無効化する。"
 *
 * <p>実装方針:
 * <ul>
 *   <li>座標 (x, y, z) を long に pack し、{@code WeakReference<TileEntity>} にマップする。</li>
 *   <li>{@code getTileEntity} が null を返したとき（chunk unload 等）はキャッシュから除去する。</li>
 *   <li>キャッシュされた TE が {@code isInvalid()} の場合も除去して再取得する。</li>
 *   <li>このキャッシュは Mixin target の per-instance フィールドとして保持する。</li>
 * </ul>
 *
 * <p>安全性: {@code WeakReference} を使うため GC に対して透過的。
 * Chunk unload 時に TE が GC されると自動的にキャッシュが無効化される。
 */
@Mixin(targets = "jp.masa.signalcontrollermod.block.tileentity.TileEntitySignalController", remap = false)
public abstract class SignalControllerTileEntityCacheMixin {

    /** pack(x, y, z) → WeakReference<TileEntity> のキャッシュ。インスタンスごとに保持。 */
    @Unique
    private final Map<Long, WeakReference<TileEntity>> crosstie$teCache = new HashMap<>();

    /**
     * {@code updateEntity()} および {@code getSignal()} 内の
     * {@code World.getTileEntity(x, y, z)} 呼び出しをキャッシュ経由に差し替える。
     *
     * <p>require = 0 のため、対象メソッドが存在しない場合は何もしない。
     */
    @Redirect(
            method = {"updateEntity", "getSignal", "searchSignalAboveY"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getTileEntity(III)Lnet/minecraft/tileentity/TileEntity;"),
            require = 0,
            remap = true)
    private TileEntity crosstie$cachedGetTileEntity(World world, int x, int y, int z) {
        long key = crosstie$packPos(x, y, z);

        WeakReference<TileEntity> ref = crosstie$teCache.get(key);
        if (ref != null) {
            TileEntity cached = ref.get();
            if (cached != null && !cached.isInvalid()) {
                if (CrossTieDiagnostics.isEnabled()) {
                    CrossTieDiagnostics.reflectionCacheHits.incrementAndGet();
                }
                return cached;
            }
            // 無効化されたキャッシュを除去
            crosstie$teCache.remove(key);
        }

        // キャッシュミス: 実際に getTileEntity を呼ぶ
        TileEntity te = world.getTileEntity(x, y, z);
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.reflectionCacheMisses.incrementAndGet();
        }
        if (te != null) {
            crosstie$teCache.put(key, new WeakReference<>(te));
        }
        return te;
    }

    /**
     * chunk unload や block 変更でキャッシュが古くなる可能性があるため、
     * {@code updateEntity()} の末尾で古くなった TE をキャッシュから掃除する。
     *
     * <p>毎 tick 全件走査はコストが高いため、無効な TE の削除のみを行う。
     */
    @Unique
    protected void crosstie$cleanStaleCache() {
        crosstie$teCache.entrySet().removeIf(entry -> {
            WeakReference<TileEntity> ref = entry.getValue();
            TileEntity te = ref.get();
            return te == null || te.isInvalid();
        });
    }

    /** (x, y, z) を long に pack する。y は 0-255 を想定。 */
    @Unique
    private static long crosstie$packPos(int x, int y, int z) {
        // x: bits 42-63 (22 bits), y: bits 34-41 (8 bits), z: bits 12-33 (22 bits)
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFF) << 34) | ((long) (z & 0x3FFFFF) << 12);
    }
}
