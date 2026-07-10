package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.NGTRenderHelper;
import jp.ngt.ngtlib.renderer.NGTTessellator;
import jp.ngt.ngtlib.util.NGTUtilClient;
import jp.ngt.rtm.gui.InternalButton;
import jp.ngt.rtm.gui.InternalGUI;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Angelica等でのGL_SELECTの不具合を根本的に回避するため、
 * 3D空間上のGUIボタンの四隅を画面上の2D座標に投影（gluProject）し、
 * 数学的な計算のみでマウス判定を行うように描画パスごと上書きするMixin。
 */
@Mixin(value = InternalGUI.class, remap = false)
public abstract class InternalGUIMathPickingMixin {

    @Shadow
    private float startX;
    @Shadow
    private float startY;
    @Shadow
    private float width;
    @Shadow
    private float height;
    @Shadow
    private int color;
    @Shadow
    public List<InternalButton> buttons;
    @Shadow
    public boolean mouseClicked;

    @Shadow
    protected abstract void clickButton(InternalButton button);

    /**
     * @author Suzumiya
     * @reason GL_SELECTを完全に排除し、gluProjectによる正確な数学的ピッキングに置き換える。
     */
    @Overwrite
    public void render() {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        NGTTessellator tessellator = NGTTessellator.instance;
        tessellator.startDrawing(7);
        tessellator.setColorRGBA_I(this.color, 176);
        NGTRenderHelper.addQuadGuiFaceWithSize(this.startX, this.startY, this.width, this.height, 0.0F);
        tessellator.draw();

        GL11.glDisable(GL11.GL_BLEND);

        FloatBuffer modelMatrix = BufferUtils.createFloatBuffer(16);
        FloatBuffer projMatrix = BufferUtils.createFloatBuffer(16);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelMatrix);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projMatrix);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        // RTMの3D GUIは画面中央（クロスヘア）で照準を合わせて右クリックするため、
        // マウス位置は常に画面の中央とする。
        float mouseX = (float) org.lwjgl.opengl.Display.getWidth() / 2.0F;
        float mouseY = (float) org.lwjgl.opengl.Display.getHeight() / 2.0F;

        boolean mouseDown = Mouse.isButtonDown(1);
        if (!mouseDown && NGTUtilClient.getMinecraft().inGameHasFocus) {
            this.mouseClicked = false;
        }

        FloatBuffer winPos = BufferUtils.createFloatBuffer(3);

        for (InternalButton button : this.buttons) {
            InternalButtonAccessor acc = (InternalButtonAccessor) button;
            float bx = acc.getStartX();
            float by = acc.getStartY();
            float bw = acc.getWidth();
            float bh = acc.getHeight();

            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;

            float[][] corners = { { bx, by }, { bx + bw, by }, { bx, by + bh }, { bx + bw, by + bh } };

            for (float[] c : corners) {
                // z=0.01fはInternalButtonの描画オフセット
                GLU.gluProject(c[0], c[1], 0.01f, modelMatrix, projMatrix, viewport, winPos);
                float px = winPos.get(0);
                float py = winPos.get(1);
                float pz = winPos.get(2);
                if (px < minX)
                    minX = px;
                if (px > maxX)
                    maxX = px;
                if (py < minY)
                    minY = py;
                if (py > maxY)
                    maxY = py;
                if (pz < minZ)
                    minZ = pz;
            }

            boolean inBounds = (mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY);

            if (inBounds) {
                button.hovered = true;
                if (!this.mouseClicked && mouseDown && NGTUtilClient.getMinecraft().inGameHasFocus) {
                    this.clickButton(button);
                    this.mouseClicked = true;
                }
            } else {
                button.hovered = false;
            }

            // 本来のGL_SELECT用パスを省略し、通常の描画パスのみ実行
            button.render(false);
        }

        GL11.glPopMatrix();
    }
}
