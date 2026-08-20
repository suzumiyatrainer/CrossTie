package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P-10: Point#getActiveRailMap() の checkRSInput() 二重呼び出しを解消する。
 *
 * <h3>問題</h3>
 * <ul>
 * <li>{@code Point#onUpdate()} が毎 tick {@code checkRSInput()} を呼ぶ。</li>
 * <li>{@code Point#getActiveRailMap()} も呼び出し毎(=描画フレーム毎)に {@code checkRSInput()}
 * を重複して呼ぶ。</li>
 * <li>{@code World.isBlockIndirectlyGettingPowered()} は内部で6方向の隣接ブロックを
 * 走査するため、チャンクアクセスを伴う。</li>
 * <li>分岐レール1本につき Point が3〜4個存在するため、描画フレーム毎に 12〜16回の冗長な呼び出しが発生する。</li>
 * </ul>
 *
 * <h3>解決策</h3>
 * <ul>
 * <li>{@code onUpdate()} の先頭で RS 状態を1回取得してフィールドにキャッシュする。</li>
 * <li>{@code onUpdate()} 内部の {@code checkRSInput()} 呼び出しを {@code @Redirect}
 * でキャッシュ参照に置き換え(重複排除)。</li>
 * <li>{@code getActiveRailMap()} 内部の {@code checkRSInput()} 呼び出しも
 * {@code @Redirect} でキャッシュ参照に置き換える。</li>
 * </ul>
 *
 * <h3>キャッシュ設計</h3>
 * <ul>
 * <li>キャッシュ対象: RS 入力状態(boolean)のみ。トポロジー情報は対象外。</li>
 * <li>キャッシュ期間: 1 tick (次の {@code onUpdate()} 呼び出しで上書き)。</li>
 * <li>初回 tick 前({@code rsInputCached == false})はフォールバックで 実際に
 * {@code checkRSInput()} を呼ぶ。</li>
 * </ul>
 */
@Mixin(value = Point.class, remap = false)
public abstract class PointRSCacheMixin {

    /** onUpdate() が最低1回呼ばれてキャッシュが有効かどうか */
    @Unique
    private boolean crosstie$rsInputCached = false;

    /** キャッシュされた RS 入力状態 */
    @Unique
    private boolean crosstie$cachedRSInput = false;

    /**
     * {@code onUpdate()} の先頭で RS 状態をキャッシュする。 このあと元の {@code onUpdate()} 本体が走るが、内部の
     * {@code checkRSInput()} は下の {@code @Redirect} で横取りされる。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void crosstie$preCacheRSInput(World world, CallbackInfo ci) {
        Point self = (Point) (Object) this;
        this.crosstie$cachedRSInput = self.rpRoot.checkRSInput(world);
        this.crosstie$rsInputCached = true;
    }

    /**
     * {@code onUpdate()} 内の {@code checkRSInput()} 呼び出しを キャッシュ参照に置き換える。 HEAD
     * で既に取得済みなので重複呼び出しを 0 にする。
     */
    @Redirect(method = "onUpdate", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/rail/util/RailPosition;checkRSInput(Lnet/minecraft/world/World;)Z"))
    private boolean crosstie$redirectOnUpdateRSCheck(RailPosition rp, World world) {
        // HEAD で既にキャッシュ済みのため、そのまま返す
        return this.crosstie$cachedRSInput;
    }

    /**
     * {@code getActiveRailMap()} 内の {@code checkRSInput()} 呼び出しを キャッシュ参照に置き換える。
     * 描画フレーム毎の余分なチャンクアクセスを削減する。 キャッシュ未設定時(初回 tick 前)はフォールバックで実際に呼ぶ。
     */
    @Redirect(method = "getActiveRailMap", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/rail/util/RailPosition;checkRSInput(Lnet/minecraft/world/World;)Z"))
    private boolean crosstie$redirectGetActiveRSCheck(RailPosition rp, World world) {
        if (this.crosstie$rsInputCached) {
            return this.crosstie$cachedRSInput;
        }
        // キャッシュ未設定の場合のみ実際に呼ぶ(安全フォールバック)
        return rp.checkRSInput(world);
    }
}
