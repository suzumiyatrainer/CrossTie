package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.util.NGTUtilClient;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailDir;
import jp.ngt.rtm.rail.util.SwitchType;
import jp.ngt.rtm.render.PartsRenderer;
import jp.ngt.rtm.render.RailPartsRenderer;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.accessors.rtm.ISwitchDynamicDisplayListHolder;
import net.suzumiya.crosstie.client.CrossTieRailCompileTextureHook;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.script.ScriptEngine;

/**
 * 2-3, 2-6, Phase 5-4: {@link RailPartsRenderer} の安全な最適化。
 *
 * <ul>
 *   <li>スクリプト無し（{@code script == null}）または {@code renderRailDynamic} が空 {@code {}} / {@code { return; }} のみの
 *       スクリプト（{@code RenderAR_01/02.js} 含む）は {@code usedStaticGeometry} の有無を問わず即スキップ。</li>
 *   <li>{@code compileStaticRailParts} で {@code renderStaticPartsGeometry} が呼ばれなかったカスタムスクリプト
 *       （{@code RenderAR_01/02.js}, {@code hi03} 等）の {@code staticRenderKey} が
 *       {@code BRIGHTNESS_PENDING_RENDER_KEY} のまま残って毎フレーム再コンパイルループになる不具合を修正。</li>
 *   <li>標準系スクリプト（{@code renderStaticPartsGeometry} 使用＋{@code if (renderer.isSwitchRail(...)) { ... }} 単体）では、
 *       通常レールはスキップ、静止分岐レールは {@code bindTexture} を外出しした DisplayList キャッシュで再生。</li>
 * </ul>
 */
@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailPartsRendererScriptGuardMixin {

    @Unique
    private static final int CROSSTIE$BRIGHTNESS_PENDING_KEY = Integer.MIN_VALUE;

    @Shadow
    protected int currentRailIndex;

    @Unique
    private boolean crosstie$usedStaticGeometry = false;

    /** 0 = 未判定, 1 = 標準（isSwitchRail のみ）, 2 = カスタム（常に実行）, 3 = 完全空/return のみ（常時スキップ） */
    @Unique
    private byte crosstie$dynamicScriptMode = 0;

    @Unique
    private boolean crosstie$compilingSwitchDynamic = false;

    @Unique
    private ResourceLocation crosstie$switchDynamicTexture = null;

    @Unique
    private boolean crosstie$switchDynamicMultiTexture = false;

    @Inject(method = "renderStaticPartsGeometry", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$markStaticGeometryUsed(TileEntityLargeRailCore tileEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!this.crosstie$usedStaticGeometry) {
            this.crosstie$usedStaticGeometry = true;
            // 未確定時に暫定で mode=2 にキャッシュされていた場合、再評価を許可する
            if (this.crosstie$dynamicScriptMode == 2) {
                this.crosstie$dynamicScriptMode = 0;
            }
        }
    }

    /**
     * Phase 5-4: {@code compileStaticRailParts} 完了時に {@code renderStaticPartsGeometry} が呼ばれなかった場合
     * （{@code RenderAR_01/02.js}, {@code hi03} 等）、{@code staticRenderKey} が初期値
     * {@code BRIGHTNESS_PENDING_RENDER_KEY} のまま残って毎フレーム再コンパイルし続ける暴走を防止する。
     */
    @Inject(method = "compileStaticRailParts", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$clearPendingKeyAfterCustomStaticCompile(
            TileEntityLargeRailCore tileEntity, double x, double y, double z, float par8, CallbackInfo ci) {
        if (tileEntity != null
                && tileEntity.getStaticRenderKey(this.currentRailIndex) == CROSSTIE$BRIGHTNESS_PENDING_KEY
                && !tileEntity.shouldRerenderRail) {
            tileEntity.setStaticRenderKey(this.currentRailIndex, 0);
        }
    }

    @Unique
    private byte crosstie$resolveDynamicScriptMode(ScriptEngine script) {
        if (this.crosstie$dynamicScriptMode != 0) {
            return this.crosstie$dynamicScriptMode;
        }
        try {
            Object srcObj = script.eval("typeof renderRailDynamic === 'function' ? String(renderRailDynamic) : ''");
            String src = srcObj != null ? srcObj.toString() : "";
            // 本体完全空または return; のみなら usedStaticGeometry の有無に関わらず常時スキップ安全
            if (crosstie$isAlwaysEmptyBody(src)) {
                this.crosstie$dynamicScriptMode = 3;
                return 3;
            }
            // renderRailStatic で標準ジオメトリを描画していないレンダラ（hi03 等）はスキップ・ラップしない
            if (!this.crosstie$usedStaticGeometry) {
                this.crosstie$dynamicScriptMode = 2;
                return 2;
            }
            if (crosstie$isSwitchOnlyBody(src)) {
                this.crosstie$dynamicScriptMode = 1;
            } else {
                this.crosstie$dynamicScriptMode = 2;
            }
        } catch (Throwable t) {
            this.crosstie$dynamicScriptMode = 2;
        }
        return this.crosstie$dynamicScriptMode;
    }

    @Unique
    private static String crosstie$extractBody(String fnSource) {
        if (fnSource == null || fnSource.isEmpty()) {
            return null;
        }
        String noBlockComments = fnSource.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        String noComments = noBlockComments.replaceAll("//[^\\r\\n]*", "");
        int openBrace = noComments.indexOf('{');
        int closeBrace = noComments.lastIndexOf('}');
        if (openBrace < 0 || closeBrace <= openBrace) {
            return null;
        }
        return noComments.substring(openBrace + 1, closeBrace).trim();
    }

    @Unique
    private static boolean crosstie$isAlwaysEmptyBody(String fnSource) {
        String body = crosstie$extractBody(fnSource);
        if (body == null) {
            return false;
        }
        if (body.isEmpty()) {
            return true;
        }
        String compact = body.replaceAll("\\s+", "");
        return "return;".equals(compact) || "return".equals(compact);
    }

    /**
     * JS 関数ソースの本体が単一の {@code if (renderer.isSwitchRail(...)) { ... }}（{@code else} 無し）のみで構成されているか判定する。
     */
    @Unique
    private static boolean crosstie$isSwitchOnlyBody(String fnSource) {
        String body = crosstie$extractBody(fnSource);
        if (body == null || body.isEmpty()) {
            return false;
        }
        if (body.contains("else")) {
            return false;
        }
        String compact = body.replaceAll("\\s+", "");
        if (!compact.startsWith("if(renderer.isSwitchRail(")) {
            return false;
        }
        int condEnd = crosstie$findMatching(compact, 2, '(', ')');
        if (condEnd < 0 || condEnd + 1 >= compact.length() || compact.charAt(condEnd + 1) != '{') {
            return false;
        }
        int blockEnd = crosstie$findMatching(compact, condEnd + 1, '{', '}');
        return blockEnd == compact.length() - 1;
    }

    @Unique
    private static int crosstie$findMatching(String s, int startIdx, char open, char close) {
        int depth = 0;
        for (int i = startIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Inject(method = "renderRailDynamic", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$guardAndCacheRenderRailDynamic(TileEntityLargeRailCore tileEntity, double x, double y, double z,
            float par8, CallbackInfo ci) {
        if (this.crosstie$compilingSwitchDynamic) {
            return;
        }

        ScriptEngine script = (ScriptEngine) ((PartsRenderer<?, ?>) (Object) this).getScript();
        if (script == null) {
            ci.cancel();
            return;
        }

        byte mode = this.crosstie$resolveDynamicScriptMode(script);
        if (mode == 3) {
            // 完全空または return; のみ → 通常・分岐問わず常時スキップ
            ci.cancel();
            return;
        }
        if (mode != 1) {
            return;
        }

        // 1. 通常レール: 標準系スクリプトは完全 no-op のため JS 呼び出しをスキップ
        if (!(tileEntity instanceof TileEntityLargeRailSwitchCore)) {
            ci.cancel();
            return;
        }

        // 2. 分岐レール: 全 Point が静止中のとき DisplayList でキャッシュ描画
        if (this.currentRailIndex != 0 || GLHelper.isCompiling() || this.crosstie$switchDynamicMultiTexture) {
            return;
        }
        TileEntityLargeRailSwitchCore switchCore = (TileEntityLargeRailSwitchCore) tileEntity;
        SwitchType st = switchCore.getSwitch();
        if (st == null || st.getPoints() == null) {
            return;
        }

        Point[] points = st.getPoints();
        int pointStateMask = 0;
        for (int i = 0; i < points.length; i++) {
            Point p = points[i];
            if (p == null || p.branchDir == RailDir.NONE) {
                continue;
            }
            float mov = p.getMovement();
            if (mov > 0.0F && mov < 1.0F) {
                return;
            }
            if (mov >= 1.0F) {
                pointStateMask |= (1 << i);
            }
        }

        if (!(tileEntity instanceof ISwitchDynamicDisplayListHolder)) {
            return;
        }
        ISwitchDynamicDisplayListHolder holder = (ISwitchDynamicDisplayListHolder) tileEntity;
        int skySub = (tileEntity.getWorldObj() != null) ? tileEntity.getWorldObj().skylightSubtracted : 0;
        // shouldRerenderRail 自体は未ロードチャンクで true が残り毎フレーム再コンパイルを招くため、
        // pointStateMask + staticRenderKey + skylightSubtracted で安定判定する
        int stateKey = (31 * pointStateMask + tileEntity.getStaticRenderKey(0)) * 31 + skySub;
        DisplayList dl = holder.crosstie$getSwitchDynamicGLList();

        boolean needsCompile = !GLHelper.isValid(dl)
                || holder.crosstie$getSwitchDynamicStateKey() != stateKey;

        if (needsCompile) {
            dl = GLHelper.generateGLList(dl);
            holder.crosstie$setSwitchDynamicGLList(dl);
            this.crosstie$compilingSwitchDynamic = true;
            CrossTieRailCompileTextureHook.beginCapture();
            GLHelper.startCompile(dl);
            try {
                ScriptUtil.doScriptFunction(script, "renderRailDynamic", tileEntity, 0.0D, 0.0D, 0.0D, par8, 0);
            } finally {
                GLHelper.endCompile();
                ResourceLocation captured = CrossTieRailCompileTextureHook.endCaptureGetTexture();
                if (CrossTieRailCompileTextureHook.hadMultipleTextures()) {
                    this.crosstie$switchDynamicMultiTexture = true;
                    GLHelper.deleteGLList(dl);
                    holder.crosstie$setSwitchDynamicGLList(null);
                    this.crosstie$compilingSwitchDynamic = false;
                    return;
                }
                if (captured != null) {
                    this.crosstie$switchDynamicTexture = captured;
                }
                this.crosstie$compilingSwitchDynamic = false;
            }
            holder.crosstie$setSwitchDynamicStateKey(stateKey);
        }

        if (GLHelper.isValid(dl)) {
            if (this.crosstie$switchDynamicTexture != null) {
                NGTUtilClient.bindTexture(this.crosstie$switchDynamicTexture);
            }
            GL11.glPushMatrix();
            GL11.glTranslatef((float) x, (float) y, (float) z);
            GLHelper.callList(dl);
            GL11.glPopMatrix();
            ci.cancel();
        }
    }
}
