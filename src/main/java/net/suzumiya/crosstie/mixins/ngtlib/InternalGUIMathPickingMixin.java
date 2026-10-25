package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.NGTRenderHelper;
import jp.ngt.ngtlib.renderer.NGTTessellator;
import jp.ngt.ngtlib.util.NGTUtilClient;
import jp.ngt.rtm.gui.InternalButton;
import jp.ngt.rtm.gui.InternalGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.suzumiya.crosstie.utils.TrueGL;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * AngelicaやOptiFine等でのGL_SELECT不具合を完全に回避し、
 * 視線レイと3Dボタン平面の厳密な交差計算（Ray-Plane Intersection）を行うMixin。
 *
 * 従来の gluProject + AABB では以下の致命的欠陥があった：
 * 1. カメラ背後にあるマーカーの投影座標が正負反転し、画面全体を覆う巨大なAABBを生成して誤爆する
 * 2. 視線に対して斜めのボタンをAABBで囲むため、本来のボタン領域外でも反応する
 * 3. 視点前方の手前/奥のデプス判定やブロック遮蔽判定がなく、奥や地面下のマーカーが誤爆する
 *
 * 本実装では ModelView 行列から直接ローカル基底ベクトルを取り出し、
 * 画面中央の視線レイ (0, 0, -t) とボタン平面 (z = 0.01) の交点を純粋な幾何計算で求める。
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

    private static final FloatBuffer crosstie$modelMatrix = BufferUtils.createFloatBuffer(16);

    /**
     * @author Suzumiya
     * @reason GL_SELECTおよび不正確なgluProject AABB判定を廃止し、幾何学的に厳密なRay-Plane交差判定に置き換える。
     */
    @Overwrite
    public void render() {
        GL11.glPushMatrix();

        boolean useShaderCompat = false;
        int savedTex = 0;
        try {
            Class<?> shadersClass = Class.forName("shadersmod.client.Shaders");
            java.lang.reflect.Field field = shadersClass.getDeclaredField("shaderPackLoaded");
            field.setAccessible(true);
            if (field.getBoolean(null)) {
                useShaderCompat = true;
            }
        } catch (Throwable ignored) {}

        if (useShaderCompat) {
            savedTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.suzumiya.crosstie.utils.MarkerRenderState.getWhiteTexture());
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        NGTTessellator tessellator = NGTTessellator.instance;
        tessellator.startDrawing(7);
        tessellator.setColorRGBA_I(this.color, 176);
        NGTRenderHelper.addQuadGuiFaceWithSize(this.startX, this.startY, this.width, this.height, 0.0F);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);

        // Angelica/バニラ/OptiFine両対応で行列取得
        TrueGL.glGetFloat(GL11.GL_MODELVIEW_MATRIX, crosstie$modelMatrix);

        // ModelView 行列 (column-major) から視点空間基底ベクトルと原点を抽出
        float uX = crosstie$modelMatrix.get(0),  uY = crosstie$modelMatrix.get(1),  uZ = crosstie$modelMatrix.get(2);
        float vX = crosstie$modelMatrix.get(4),  vY = crosstie$modelMatrix.get(5),  vZ = crosstie$modelMatrix.get(6);
        float nX = crosstie$modelMatrix.get(8),  nY = crosstie$modelMatrix.get(9),  nZ = crosstie$modelMatrix.get(10);
        float p0X = crosstie$modelMatrix.get(12), p0Y = crosstie$modelMatrix.get(13), p0Z = crosstie$modelMatrix.get(14);

        // ボタン面のローカルオフセット z = 0.01f
        float qX = p0X + 0.01f * nX;
        float qY = p0Y + 0.01f * nY;
        float qZ = p0Z + 0.01f * nZ;

        boolean canInteract = false;
        float localHitX = 0.0f;
        float localHitY = 0.0f;
        float hitDistance = 0.0f;

        // ボタン面がカメラ側を向いているか判定 (視線 (0,0,-1) と法線 N の内積 < 0 <=> -nZ < 0 <=> nZ > 0)
        if (nZ > 1e-4f) {
            float qDotN = qX * nX + qY * nY + qZ * nZ;
            float t = qDotN / nZ;

            // カメラ前方かつ操作有効距離内 (0.05m < t <= 64.0m)
            if (t > 0.05f && t <= 64.0f) {
                // 視点空間での交点 H = (0, 0, -t)
                // 差分 delta = H - Q = (-qX, -qY, -t - qZ)
                float dX = -qX;
                float dY = -qY;
                float dZ = -t - qZ;

                float uLenSq = uX * uX + uY * uY + uZ * uZ;
                float vLenSq = vX * vX + vY * vY + vZ * vZ;

                if (uLenSq > 1e-6f && vLenSq > 1e-6f) {
                    localHitX = (dX * uX + dY * uY + dZ * uZ) / uLenSq;
                    localHitY = (dX * vX + dY * vY + dZ * vZ) / vLenSq;
                    hitDistance = t;
                    canInteract = true;
                }
            }
        }

        // ブロック遮蔽判定（地形や壁の向こう側にあるマーカーの誤クリック防止）
        if (canInteract) {
            Minecraft mc = NGTUtilClient.getMinecraft();
            if (mc.theWorld != null && mc.thePlayer != null) {
                double eyeX = RenderManager.instance.viewerPosX;
                double eyeY = RenderManager.instance.viewerPosY;
                double eyeZ = RenderManager.instance.viewerPosZ;
                Vec3 look = mc.thePlayer.getLook(1.0F);
                if (look != null) {
                    Vec3 eyeVec = Vec3.createVectorHelper(eyeX, eyeY, eyeZ);
                    Vec3 hitVec = Vec3.createVectorHelper(
                        eyeX + look.xCoord * hitDistance,
                        eyeY + look.yCoord * hitDistance,
                        eyeZ + look.zCoord * hitDistance
                    );
                    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyeVec, hitVec, false);
                    if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                        canInteract = false;
                    }
                }
            }
        }

        boolean mouseDown = Mouse.isButtonDown(1);
        if (!mouseDown && NGTUtilClient.getMinecraft().inGameHasFocus) {
            this.mouseClicked = false;
        }

        for (InternalButton button : this.buttons) {
            InternalButtonAccessor acc = (InternalButtonAccessor) button;
            float bx = acc.getStartX();
            float by = acc.getStartY();
            float bw = acc.getWidth();
            float bh = acc.getHeight();

            boolean inBounds = canInteract
                && (localHitX >= bx && localHitX <= bx + bw)
                && (localHitY >= by && localHitY <= by + bh);

            if (inBounds) {
                button.hovered = true;
                if (!this.mouseClicked && mouseDown && NGTUtilClient.getMinecraft().inGameHasFocus) {
                    this.clickButton(button);
                    this.mouseClicked = true;
                }
            } else {
                button.hovered = false;
            }

            if (useShaderCompat) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.suzumiya.crosstie.utils.MarkerRenderState.getWhiteTexture());
            }

            button.render(false);
        }

        if (useShaderCompat) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTex);
        }

        GL11.glPopMatrix();
    }
}
