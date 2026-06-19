package net.suzumiya.crosstie.mixins.gtnhlib;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockTripWireHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GTNHLibのモデル管理から特定ブロックを除外するアイコンフォールバック統合Mixin。
 *
 * <h3>背景</h3>
 * GTNHLib の {@link com.gtnewhorizon.gtnhlib.core.fml.transformers.BlockIconTransformer
 * BlockIconTransformer}(IClassTransformer) は ASM 変換により Block クラス(親)にのみ
 * {@code nhlib$setModeled}/{@code nhlib$isModeled} メソッドと
 * {@code getIcon(...)} への早期リターンフックを追加します。
 * これによりモデル管理対象ブロックは missingno (テクスチャ欠落) を返すようになります。
 *
 * <h3>問題</h3>
 * 各サブクラス(BlockBed, BlockRedstoneWire, BlockTripWire, BlockTripWireHook)を個別に
 * {@code @Mixin} すると、Mixin エンジンは親クラスのメソッドを解決できず
 * ({@code nhlib$setModeled} はサブクラスに直接存在しないため)、
 * {@code require=0} により注入が静かにスキップされます。
 * また、複数の Mixin クラスが Block.class の同じメソッドに注入すると
 * バイトコード競合によりクラスファイルが破損します({@code NoClassDefFoundError} 等)。
 *
 * <h3>修正方針</h3>
 * {@code @Mixin(Block.class)} + {@code instanceof} チェックで1クラスに統合します。
 */
@Mixin(Block.class)
public abstract class MixinBlockIconFallback {

    // ========== instanceof チェック用ブロック型リスト ==========

    private boolean isTargetBlock() {
        return (Object) this instanceof BlockBed
                || (Object) this instanceof BlockRedstoneWire
                || (Object) this instanceof BlockTripWire
                || (Object) this instanceof BlockTripWireHook;
    }

    // ========== nhlib$setModeled フック ==========

    /**
     * GTNHLib が {@code nhlib$setModeled(true)} を呼んでも
     * 対象ブロックの場合は常にキャンセルする。
     */
    @Inject(method = "nhlib$setModeled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$blockSetModeled(boolean modeled, CallbackInfo ci) {
        if (isTargetBlock()) {
            ci.cancel();
        }
    }

    // ========== nhlib$isModeled フック ==========

    /**
     * {@code nhlib$isModeled()} が呼ばれた場合も
     * 対象ブロックの場合は強制的に {@code false} を返す。
     */
    @Inject(method = "nhlib$isModeled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$forceNotModeled(CallbackInfoReturnable<Boolean> cir) {
        if (isTargetBlock()) {
            cir.setReturnValue(false);
        }
    }
}