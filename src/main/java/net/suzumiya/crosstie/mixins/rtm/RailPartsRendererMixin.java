package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.rtm.modelpack.modelset.ModelSetRailClient;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.RailPartsRenderer;
import jp.ngt.rtm.render.TileEntityPartsRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.suzumiya.crosstie.client.CrossTieRenderFrameTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.WeakHashMap;

/**
 * 2-3: {@link RailPartsRenderer} の包括的最適化 Mixin。
 *
 * <ul>
 *   <li>{@code createMatrix}: {@code NGTRenderHelper.translate/rotate/multiplyMatrix} による毎セグメント配列 20 個＋
 *       {@code FloatBuffer} 4 個のアロケーションを廃止し、4x4 変換行列を直接展開＋{@code ThreadLocal<FloatBuffer>} 再利用。</li>
 *   <li>{@code getRailBrightness}: 同一ブロック座標の連続クエリをキャッシュして重複ワールドアクセスを排除。</li>
 *   <li>{@code getBrightness}: 10 tick 単位のクライアントブロック座標キャッシュを導入（{@code hi03} 等の外部パックの
 *       毎フレーム数万回チャンク参照を直撃高速化）。</li>
 *   <li>{@code isSwitchRail}: {@code getAllRailMaps()} の配列生成を廃止し {@code instanceof} で O(1) 判定。</li>
 *   <li>{@code createModelRenderKey}: モデル単位で不変のキーをキャッシュし毎回の {@code Arrays.stream} を排除。</li>
 *   <li>{@code areRailBrightnessPositionsLoaded}: 未ロードチャンク時の毎フレーム {@code createRailGeometry} 暴走を
 *       20 フレーム間隔に間引き。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailPartsRendererMixin extends TileEntityPartsRenderer<ModelSetRailClient> {

    @Shadow
    public abstract ModelObject getModelObject();

    @Shadow
    public abstract String[] getAllObjNames();

    @Shadow
    public abstract int getBrightness(World world, int x, int y, int z);

    @Unique
    private static final ThreadLocal<FloatBuffer> CROSSTIE_MATRIX_BUF = new ThreadLocal<FloatBuffer>() {
        @Override
        protected FloatBuffer initialValue() {
            return FloatBuffer.allocate(1024 << 4);
        }
    };

    /** 未ロードチャンク判定が false だった TileEntity の直近チェックフレーム番号 */
    @Unique
    private static final WeakHashMap<TileEntityLargeRailCore, Integer> CROSSTIE_UNLOADED_CHECK_FRAME =
            new WeakHashMap<TileEntityLargeRailCore, Integer>();

    @Unique
    private static final int UNLOADED_RECHECK_INTERVAL_FRAMES = 20;

    // 10 tick 単位のブロック明るさキャッシュ（クライアントメインスレッド専用、オープンアドレッシング）
    @Unique
    private static final int BRIGHTNESS_CACHE_SIZE = 4096;
    @Unique
    private static final int BRIGHTNESS_CACHE_MASK = BRIGHTNESS_CACHE_SIZE - 1;
    @Unique
    private static World crosstie$brightnessCacheWorld;
    @Unique
    private static long crosstie$brightnessCacheBucket = Long.MIN_VALUE;
    @Unique
    private static final long[] crosstie$brightnessKeys = new long[BRIGHTNESS_CACHE_SIZE];
    @Unique
    private static final int[] crosstie$brightnessVals = new int[BRIGHTNESS_CACHE_SIZE];
    @Unique
    private static final boolean[] crosstie$brightnessOccupied = new boolean[BRIGHTNESS_CACHE_SIZE];

    @Unique
    private boolean crosstie$hasModelRenderKey = false;
    @Unique
    private int crosstie$cachedModelRenderKey = 0;

    /**
     * @author CrossTie
     * @reason 毎セグメントの配列 20 個＋FloatBuffer 4 個アロケーションを廃止し、T*Ry*Rx*Rz を直接 4x4 展開する。
     */
    @Overwrite
    private FloatBuffer createMatrix(float[][] rp) {
        int count = rp.length;
        int required = count << 4;
        FloatBuffer buffer = CROSSTIE_MATRIX_BUF.get();
        if (buffer.capacity() < required) {
            buffer = FloatBuffer.allocate(required);
            CROSSTIE_MATRIX_BUF.set(buffer);
        }
        buffer.clear();
        buffer.limit(required);

        float[] arr = buffer.array();
        int offset = 0;
        for (int i = 0; i < count; i++) {
            float[] p = rp[i];
            float tx = p[0];
            float ty = p[1];
            float tz = p[2];

            float ry = NGTMath.toRadians(p[3]);
            float rx = NGTMath.toRadians(p[4]);
            float rz = NGTMath.toRadians(p[5]);

            float sy = NGTMath.getSin(ry);
            float cy = NGTMath.getCos(ry);
            float sx = NGTMath.getSin(rx);
            float cx = NGTMath.getCos(rx);
            float sz = NGTMath.getSin(rz);
            float cz = NGTMath.getCos(rz);

            float sysx = sy * sx;
            float cysx = cy * sx;

            // col 0
            arr[offset] = cy * cz + sysx * sz;
            arr[offset + 1] = cx * sz;
            arr[offset + 2] = -sy * cz + cysx * sz;
            arr[offset + 3] = 0.0F;
            // col 1
            arr[offset + 4] = -cy * sz + sysx * cz;
            arr[offset + 5] = cx * cz;
            arr[offset + 6] = sy * sz + cysx * cz;
            arr[offset + 7] = 0.0F;
            // col 2
            arr[offset + 8] = sy * cx;
            arr[offset + 9] = -sx;
            arr[offset + 10] = cy * cx;
            arr[offset + 11] = 0.0F;
            // col 3
            arr[offset + 12] = tx;
            arr[offset + 13] = ty;
            arr[offset + 14] = tz;
            arr[offset + 15] = 1.0F;

            offset += 16;
        }
        buffer.position(0);
        return buffer;
    }

    /**
     * @author CrossTie
     * @reason 同一ブロック座標の連続クエリをキャッシュしつつ、KaizPatchX 1.10.4 準拠の未ロードチャンク補完を維持。
     */
    @Overwrite
    private int[] getRailBrightness(World world, double originX, double originY, double originZ, float[][] positions) {
        final int unavailable = Integer.MIN_VALUE;
        int len = positions.length;
        int[] brightness = new int[len];
        if (len == 0) {
            return brightness;
        }

        int fallback = this.getBrightness(
                world,
                MathHelper.floor_double(originX),
                MathHelper.floor_double(originY),
                MathHelper.floor_double(originZ));

        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int lastBrightness = fallback;
        boolean lastLoaded = false;
        int previous = unavailable;

        for (int i = 0; i < len; ++i) {
            float[] pos = positions[i];
            int x0 = MathHelper.floor_double(originX + (double) pos[0]);
            int y0 = MathHelper.floor_double(originY + (double) pos[1]);
            int z0 = MathHelper.floor_double(originZ + (double) pos[2]);

            if (x0 == lastX && y0 == lastY && z0 == lastZ) {
                if (lastLoaded) {
                    brightness[i] = lastBrightness;
                    previous = lastBrightness;
                    fallback = lastBrightness;
                } else {
                    brightness[i] = (previous != unavailable) ? previous : unavailable;
                }
            } else if (crosstie$isBrightnessPosLoaded(world, x0, y0, z0)) {
                lastBrightness = this.getBrightness(world, x0, y0, z0);
                brightness[i] = lastBrightness;
                previous = lastBrightness;
                fallback = lastBrightness;
                lastLoaded = true;
            } else {
                brightness[i] = (previous != unavailable) ? previous : unavailable;
                lastLoaded = false;
            }

            lastX = x0;
            lastY = y0;
            lastZ = z0;
        }

        int next = fallback;
        for (int i = len - 1; i >= 0; --i) {
            if (brightness[i] == unavailable) {
                brightness[i] = next;
            } else {
                next = brightness[i];
            }
        }
        return brightness;
    }

    @Unique
    private static boolean crosstie$isBrightnessPosLoaded(World world, int x, int y, int z) {
        if (world == null || y < 0 || y >= 256) {
            return false;
        }
        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        return chunk != null && !chunk.isEmpty() && chunk.isChunkLoaded && chunk.isLightPopulated;
    }

    /**
     * 10 tick 単位のブロック明るさキャッシュ。
     * {@code hi03} 等の外部 JS パックが毎フレーム全セグメントで {@code renderer.getBrightness()} を呼ぶ負荷を直撃削減する。
     */
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedBrightness(World world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (world == null || !world.isRemote) {
            return;
        }
        long bucket = world.getTotalWorldTime() / 10L;
        if (crosstie$brightnessCacheWorld != world || crosstie$brightnessCacheBucket != bucket) {
            crosstie$brightnessCacheWorld = world;
            crosstie$brightnessCacheBucket = bucket;
            Arrays.fill(crosstie$brightnessOccupied, false);
            return;
        }
        long key = crosstie$packBlockPos(x, y, z);
        int slot = crosstie$mix64(key) & BRIGHTNESS_CACHE_MASK;
        if (crosstie$brightnessOccupied[slot] && crosstie$brightnessKeys[slot] == key) {
            cir.setReturnValue(crosstie$brightnessVals[slot]);
        }
    }

    @Inject(method = "getBrightness", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$storeCachedBrightness(World world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (world == null || !world.isRemote) {
            return;
        }
        long key = crosstie$packBlockPos(x, y, z);
        int slot = crosstie$mix64(key) & BRIGHTNESS_CACHE_MASK;
        crosstie$brightnessKeys[slot] = key;
        crosstie$brightnessVals[slot] = cir.getReturnValue();
        crosstie$brightnessOccupied[slot] = true;
    }

    @Unique
    private static long crosstie$packBlockPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
    }

    @Unique
    private static int crosstie$mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (int) (z ^ (z >>> 33));
    }

    /**
     * @author CrossTie
     * @reason getAllRailMaps() の毎フレーム配列生成を回避し instanceof で O(1) 判定する。
     */
    @Overwrite
    public boolean isSwitchRail(TileEntityLargeRailCore tileEntity) {
        if (tileEntity instanceof TileEntityLargeRailSwitchCore) {
            return true;
        }
        if (tileEntity == null) {
            return false;
        }
        // 万一 SwitchCore 以外のカスタム Core が複数 RailMap を持つ場合のみフォールバック
        if (tileEntity.getClass() == jp.ngt.rtm.rail.TileEntityLargeRailNormalCore.class
                || tileEntity.getClass() == jp.ngt.rtm.rail.TileEntityLargeRailSlopeCore.class
                || tileEntity instanceof jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore
                || tileEntity instanceof jp.ngt.rtm.rail.TileEntityTurnTableCore) {
            return false;
        }
        jp.ngt.rtm.rail.util.RailMap[] maps = tileEntity.getAllRailMaps();
        return maps != null && maps.length > 1;
    }

    /**
     * @author CrossTie
     * @reason モデル単位で不変のレンダリングキーをキャッシュし Arrays.stream を排除。
     */
    @Overwrite
    private int createModelRenderKey() {
        if (this.crosstie$hasModelRenderKey) {
            return this.crosstie$cachedModelRenderKey;
        }
        int key = this.modelSet.getConfig().getName().hashCode();
        key = 31 * key + Arrays.hashCode(this.getAllObjNames());
        jp.ngt.ngtlib.renderer.model.TextureSet[] textures = this.getModelObject().textures;
        int texHash = 1;
        if (textures != null) {
            for (int i = 0; i < textures.length; i++) {
                jp.ngt.ngtlib.renderer.model.TextureSet ts = textures[i];
                String s = (ts == null || ts.material == null || ts.material.texture == null)
                        ? ""
                        : ts.material.texture.toString();
                texHash = 31 * texHash + s.hashCode();
            }
        }
        key = 31 * key + texHash;
        this.crosstie$cachedModelRenderKey = key;
        this.crosstie$hasModelRenderKey = true;
        return key;
    }

    /**
     * 遠方の未ロードチャンクに接するレールで毎フレーム {@code createRailGeometry()} が暴走するのを防止する。
     */
    @Inject(method = "areRailBrightnessPositionsLoaded(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$throttleUnloadedBrightnessCheck(TileEntityLargeRailCore tileEntity, CallbackInfoReturnable<Boolean> cir) {
        if (tileEntity == null) {
            return;
        }
        Integer lastFailedFrame = CROSSTIE_UNLOADED_CHECK_FRAME.get(tileEntity);
        if (lastFailedFrame != null) {
            int currentFrame = CrossTieRenderFrameTracker.getRenderFrame();
            if (currentFrame - lastFailedFrame < UNLOADED_RECHECK_INTERVAL_FRAMES
                    && currentFrame >= lastFailedFrame) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "areRailBrightnessPositionsLoaded(Ljp/ngt/rtm/rail/TileEntityLargeRailCore;)Z", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$recordUnloadedBrightnessCheck(TileEntityLargeRailCore tileEntity, CallbackInfoReturnable<Boolean> cir) {
        if (tileEntity == null) {
            return;
        }
        if (Boolean.FALSE.equals(cir.getReturnValue())) {
            CROSSTIE_UNLOADED_CHECK_FRAME.put(tileEntity, CrossTieRenderFrameTracker.getRenderFrame());
        } else {
            CROSSTIE_UNLOADED_CHECK_FRAME.remove(tileEntity);
        }
    }
}
