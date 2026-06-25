package net.suzumiya.crosstie.mixins.optifine;

import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * OptiFine/FastCraft + shadersmod 環境でワイヤーが非表示になる問題を修正する。
 *
 * <h3>問題の詳細</h3>
 * <p>shadersmod は shadow map 生成時に TESR を呼び出すが、その際に
 * {@link MinecraftForgeClient#getRenderPass()} が {@code -1} を返す。
 * RTM の {@code RenderElectricalWiring.renderWire()} は {@code pass == 0}
 * または {@code pass == 1} でのみワイヤーを描画するため、shadow pass では
 * 一切描画されず、shadow map にワイヤーが書き込まれない。</p>
 *
 * <p>その結果、シェーダーがライティング計算に shadow map を参照する通常描画で
 * ワイヤーが完全に消えているように見える。</p>
 *
 * <h3>修正内容</h3>
 * <p>{@code renderElectricalWiring} 内で {@code getRenderPass()} の呼び出しを
 * インターセプトし、戻り値が {@code -1}（shadow pass）の場合は {@code 0}（NORMAL）
 * を返すようにする。これにより shadow pass でもワイヤーが NORMAL モードで描画される。</p>
 */
@SuppressWarnings("all")
@Mixin(targets = "jp.ngt.rtm.electric.RenderElectricalWiring", remap = false)
public abstract class WireShadowPassRenderMixin {

    @Redirect(
            method = "renderElectricalWiring",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/MinecraftForgeClient;getRenderPass()I",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private int crosstie$normalizeShadowPass() {
        int pass = MinecraftForgeClient.getRenderPass();
        if (!net.suzumiya.crosstie.CrossTieConfig.fixOptiFineWireShadowPass) {
            return pass;
        }
        // shadow pass は -1 を返す。NORMAL（0）として扱い shadow map にも描画する。
        return (pass < 0) ? 0 : pass;
    }
}
