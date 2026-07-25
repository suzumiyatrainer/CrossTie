package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.state.DataMap;
import org.spongepowered.asm.mixin.Mixin;

/**
 * [DISABLED - KaizPatchX 1.10.1 非互換]
 *
 * <p>KaizPatchX 1.10.1 において DataMap は Java から Kotlin へ書き直された。
 * これにより以下の破壊的変更が発生し、旧実装はクラッシュを引き起こす：
 *
 * <ul>
 *   <li>{@code sendPacket(String, DataEntry, boolean)} → {@code private fun sendPacket(DataKey, DataEntry<*>, Boolean)}
 *       に変更されたため {@code @Shadow} で取得不可能。</li>
 *   <li>{@code map} フィールドのキー型が {@code String} から {@code DataKey}（非公開内部クラス）に変更。</li>
 *   <li>{@code set(String, DataEntry, int)} の公開シグネチャが
 *       {@code setEntry(String, DataEntry<*>, int)} に変更。</li>
 * </ul>
 *
 * <p>1.10.1 では DataMap 自体がすでに差分チェックを行うため（{@code set()} 内で
 * 旧値と比較して変更なしの場合はパケット送信しない実装になっている）、
 * このMixinが担っていた最適化の多くはターゲット側で吸収されている。
 *
 * <p>TODO: 1.10.1 の {@code DataMap.setEntry} に対して必要な追加最適化があれば
 * 新しい Mixin として実装する。
 */
@Mixin(value = DataMap.class, remap = false)
public abstract class DataMapMixin {
    // 現在は空スタブ。1.10.1 API に合わせた再実装が必要。
}
