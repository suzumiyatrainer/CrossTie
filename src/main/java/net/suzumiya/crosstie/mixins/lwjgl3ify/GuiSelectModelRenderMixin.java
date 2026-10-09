package net.suzumiya.crosstie.mixins.lwjgl3ify;

import jp.ngt.rtm.gui.GuiSelectModel;
import jp.ngt.rtm.modelpack.modelset.IModelSetClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.suzumiya.crosstie.utils.lwjgl3ify.ScriptGL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link GuiSelectModel#renderModel(IModelSetClient, Minecraft)} を Angelica の
 * {@code GLStateManager} 経由でルーティングするパッチ。
 *
 * <h3>問題の概要</h3>
 * KaizPatchX の {@code GuiSelectModel.renderModel()} は生の {@code GL11} /
 * {@code GL12} / {@code GLU.gluPerspective} を直接呼び出している。
 * lwjgl3ify + Angelica 環境では {@code GLStateManager} が行列スタックを管理しているため、
 * 生 GL 呼び出しで状態が desync し、モデルプレビューが正しく描画されない
 * (投影行列の乱れ、ライティング状態の破壊、クラッシュ等)。
 *
 * <h3>修正方針</h3>
 * メソッド全体を {@code @Overwrite} で置き換え、全 GL 操作を
 * {@link ScriptGL} 経由で {@code GLStateManager} に委譲する。
 * 例外が発生した場合は blank プレビュー（何も描かない）に劣化させてゲームを継続する。
 *
 * <h3>適用条件</h3>
 * {@code isClient && lwjgl3ify && RTM && AngelicaGlsm} の場合のみ
 * {@link net.suzumiya.crosstie.mixins.CrossTieMixinPlugin} が登録する。
 *
 * <p><b>原作:</b> LWJGL3ify-rtm-1.0.0 by 325 (LGPL-3.0-or-later)<br>
 * 本ファイルは LWJGL3ify-rtm より派生し CrossTie 向けに Mixin 形式へ改変したものです。
 */
@Mixin(value = GuiSelectModel.class, remap = false)
public abstract class GuiSelectModelRenderMixin {

    private static final Logger LOGGER = LogManager.getLogger("CrossTie/GuiSelectModelRender");
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<String>());

    /**
     * RTM のモデルプレビューレンダリングを GLStateManager 経由に切り替える。
     *
     * <p>行列スタックは {@code try-finally} で必ず復元される。
     * 何らかの例外が発生した場合はログに1回だけ警告を出して blank プレビューとして終了する。
     */
    @Overwrite(remap = false)
    public static void renderModel(IModelSetClient par1, Minecraft par2) {
        if (par1 == null || par2 == null) {
            return;
        }
        try {
            ScriptGL.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            ScriptGL.glPushMatrix();
            ScriptGL.glMatrixMode(ScriptGL.GL_PROJECTION);
            ScriptGL.glPushMatrix();
            try {
                ScriptGL.glLoadIdentity();
                crosstie$gluPerspective(80.0F, 1.0F, 5.0F, 1000.0F);
                ScriptGL.glMatrixMode(ScriptGL.GL_MODELVIEW);
                ScriptGL.glLoadIdentity();
                RenderHelper.enableStandardItemLighting();
                ScriptGL.glEnable(ScriptGL.GL_DEPTH_TEST);
                ScriptGL.glEnable(ScriptGL.GL_RESCALE_NORMAL);
                par1.renderModelInGui(par2);
            } finally {
                ScriptGL.glDisable(ScriptGL.GL_RESCALE_NORMAL);
                ScriptGL.glDisable(ScriptGL.GL_DEPTH_TEST);
                RenderHelper.disableStandardItemLighting();
                ScriptGL.glMatrixMode(ScriptGL.GL_PROJECTION);
                ScriptGL.glViewport(0, 0, par2.displayWidth, par2.displayHeight);
                ScriptGL.glPopMatrix();
                ScriptGL.glMatrixMode(ScriptGL.GL_MODELVIEW);
                ScriptGL.glPopMatrix();
                ScriptGL.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } catch (Throwable t) {
            if (WARNED.add("renderModel")) {
                LOGGER.warn("[CrossTie] GuiSelectModel モデルプレビューのレンダリングに失敗しました。" +
                        "blank プレビューを表示します（以降の同種警告は抑制されます）。", t);
            }
        }
    }

    /**
     * {@code org.lwjgl.util.glu.Project.gluPerspective} を Reflection で呼ぶ。
     *
     * <p>lwjgl3ify の lwjglx 互換レイヤーが {@code org.lwjgl.util.glu.Project} を
     * 提供しているため、実行時に解決できる。
     */
    private static void crosstie$gluPerspective(float fovy, float aspect, float zNear, float zFar)
            throws ReflectiveOperationException {
        Class<?> project = Class.forName("org.lwjgl.util.glu.Project");
        Method gluPerspective = project.getMethod("gluPerspective",
                float.class, float.class, float.class, float.class);
        gluPerspective.invoke(null,
                Float.valueOf(fovy), Float.valueOf(aspect),
                Float.valueOf(zNear), Float.valueOf(zFar));
    }
}

