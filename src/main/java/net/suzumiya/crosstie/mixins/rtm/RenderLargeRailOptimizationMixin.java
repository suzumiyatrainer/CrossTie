package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.utils.ActionPartsMathPicker;
import net.suzumiya.crosstie.utils.CrossTieDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LargeRail (大型レール) の描画最適化。
 *
 * <p>毎フレームクリアされるバッファへの時間間引きは遠距離点滅およびシャドウパスとの分離を引き起こすため廃止し、
 * 距離カリング＋シャドウパス距離カリング分離のみを行う。
 */
@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class RenderLargeRailOptimizationMixin {

    @Unique
    private static final double SHADOW_MAX_DIST_SQ = 128.0D * 128.0D;

    @Unique
    private static int crosstie$lastRenderDist = -1;
    @Unique
    private static double crosstie$cachedMaxDistSq = 256.0D * 256.0D;

    @Unique
    private static double crosstie$getMaxRenderDistanceSq() {
        int currentDist = (Minecraft.getMinecraft().gameSettings.renderDistanceChunks + 1) * 16;
        if (currentDist != crosstie$lastRenderDist) {
            crosstie$lastRenderDist = currentDist;
            double distSq = (double) currentDist * (double) currentDist;
            crosstie$cachedMaxDistSq = Math.max(distSq, 256.0D * 256.0D);
        }
        return crosstie$cachedMaxDistSq;
    }

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$cullAndThrottle(TileEntity tileEntity, double d0, double d1, double d2, float f,
            CallbackInfo ci) {

        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.largeRailRenderCalls.incrementAndGet();
        }

        if (tileEntity == null || !(tileEntity instanceof jp.ngt.rtm.rail.TileEntityLargeRailCore)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity renderView = mc.renderViewEntity;
        if (renderView == null) {
            return;
        }

        double dx = tileEntity.xCoord + 0.5D - renderView.posX;
        double dy = tileEntity.yCoord + 0.5D - renderView.posY;
        double dz = tileEntity.zCoord + 0.5D - renderView.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxDistSq = crosstie$getMaxRenderDistanceSq();

        // シャドウパス時は遠方レールの影描画を早めにカリング（点滅ゼロでシェーダー負荷軽減）
        if (CrossTieConfig.railTesrThrottleEnabled && ActionPartsMathPicker.isShadowPassActive()) {
            double shadowLimitSq = Math.min(maxDistSq, SHADOW_MAX_DIST_SQ);
            if (distSq > shadowLimitSq) {
                ci.cancel();
                return;
            }
        }

        // メインパス距離カリング（描画距離上限、最低 256m 保証）
        if (distSq > maxDistSq) {
            ci.cancel();
        }
    }

    /**
     * RenderLargeRail.renderTileEntityLargeRail() 内の subRails 用 IntStream.range(0, size) 呼び出しを
     * subRails が空のときに回避する（毎フレーム全レールでの IntStream 生成を排除）。
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "renderTileEntityLargeRail",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/IntStream;range(II)Ljava/util/stream/IntStream;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private java.util.stream.IntStream crosstie$skipEmptySubRailStream(int start, int end) {
        if (end <= start) {
            return CROSSTIE_EMPTY_INT_STREAM;
        }
        return java.util.stream.IntStream.range(start, end);
    }

    @Unique
    private static final java.util.stream.IntStream CROSSTIE_EMPTY_INT_STREAM = new java.util.stream.IntStream() {
        @Override public void forEach(java.util.function.IntConsumer action) {}
        @Override public void forEachOrdered(java.util.function.IntConsumer action) {}
        @Override public java.util.stream.IntStream filter(java.util.function.IntPredicate predicate) { return this; }
        @Override public java.util.stream.IntStream map(java.util.function.IntUnaryOperator mapper) { return this; }
        @Override public <U> java.util.stream.Stream<U> mapToObj(java.util.function.IntFunction<? extends U> mapper) { return java.util.stream.Stream.empty(); }
        @Override public java.util.stream.LongStream mapToLong(java.util.function.IntToLongFunction mapper) { return java.util.stream.LongStream.empty(); }
        @Override public java.util.stream.DoubleStream mapToDouble(java.util.function.IntToDoubleFunction mapper) { return java.util.stream.DoubleStream.empty(); }
        @Override public java.util.stream.IntStream flatMap(java.util.function.IntFunction<? extends java.util.stream.IntStream> mapper) { return this; }
        @Override public java.util.stream.IntStream distinct() { return this; }
        @Override public java.util.stream.IntStream sorted() { return this; }
        @Override public java.util.stream.IntStream peek(java.util.function.IntConsumer action) { return this; }
        @Override public java.util.stream.IntStream limit(long maxSize) { return this; }
        @Override public java.util.stream.IntStream skip(long n) { return this; }
        @Override public int[] toArray() { return new int[0]; }
        @Override public int reduce(int identity, java.util.function.IntBinaryOperator op) { return identity; }
        @Override public java.util.OptionalInt reduce(java.util.function.IntBinaryOperator op) { return java.util.OptionalInt.empty(); }
        @Override public <R> R collect(java.util.function.Supplier<R> supplier, java.util.function.ObjIntConsumer<R> accumulator, java.util.function.BiConsumer<R, R> combiner) { return supplier.get(); }
        @Override public int sum() { return 0; }
        @Override public java.util.OptionalInt min() { return java.util.OptionalInt.empty(); }
        @Override public java.util.OptionalInt max() { return java.util.OptionalInt.empty(); }
        @Override public long count() { return 0; }
        @Override public java.util.OptionalDouble average() { return java.util.OptionalDouble.empty(); }
        @Override public java.util.IntSummaryStatistics summaryStatistics() { return new java.util.IntSummaryStatistics(); }
        @Override public boolean anyMatch(java.util.function.IntPredicate predicate) { return false; }
        @Override public boolean allMatch(java.util.function.IntPredicate predicate) { return true; }
        @Override public boolean noneMatch(java.util.function.IntPredicate predicate) { return true; }
        @Override public java.util.OptionalInt findFirst() { return java.util.OptionalInt.empty(); }
        @Override public java.util.OptionalInt findAny() { return java.util.OptionalInt.empty(); }
        @Override public java.util.stream.LongStream asLongStream() { return java.util.stream.LongStream.empty(); }
        @Override public java.util.stream.DoubleStream asDoubleStream() { return java.util.stream.DoubleStream.empty(); }
        @Override public java.util.stream.Stream<Integer> boxed() { return java.util.stream.Stream.empty(); }
        @Override public java.util.stream.IntStream sequential() { return this; }
        @Override public java.util.stream.IntStream parallel() { return this; }
        @Override public java.util.PrimitiveIterator.OfInt iterator() { return java.util.Arrays.stream(new int[0]).iterator(); }
        @Override public java.util.Spliterator.OfInt spliterator() { return java.util.Spliterators.emptyIntSpliterator(); }
        @Override public boolean isParallel() { return false; }
        @Override public java.util.stream.IntStream unordered() { return this; }
        @Override public java.util.stream.IntStream onClose(Runnable closeHandler) { return this; }
        @Override public void close() {}
    };
}
