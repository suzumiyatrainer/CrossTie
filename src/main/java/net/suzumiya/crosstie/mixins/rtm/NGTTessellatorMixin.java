package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.nio.ByteOrder;

@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorMixin {

    @org.spongepowered.asm.mixin.Unique
    private static int crosstie$whiteTexture = -1;

    @Shadow
    private int vertexCount;
    @Shadow
    private int drawMode;
    @Shadow
    private boolean hasNormals;
    @Shadow
    private boolean hasBrightness;
    @Shadow
    private boolean hasColor;
    @Shadow
    private boolean hasTexture;
    @Shadow
    private int[] rawBuffer;
    @Shadow
    private int rawBufferSize;
    @Shadow
    private int rawBufferIndex;

    @Shadow
    public abstract void reset();

    @Shadow
    private boolean isDrawing;

    @Shadow
    private int drawVertexArray() {
        return 0;
    }

    private boolean crosstie$isShaderEnabled() {
        if (jp.ngt.ngtlib.util.NGTUtilClient.usingShader()) {
            return true;
        }
        try {
            Class<?> clazz = Class.forName("shadersmod.client.Shaders");
            java.lang.reflect.Field field = clazz.getDeclaredField("shaderPackLoaded");
            return field.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @author Suzumiya
     * @reason Shader有効時にもMinecraftのTessellatorを使用して描画を正常化する。
     */
    @Overwrite
    public int draw() {
        if (!this.isDrawing) {
            throw new IllegalStateException("Not tesselating!");
        }
        this.isDrawing = false;

        if (jp.kaiz.kaizpatch.compat.AngelicaCompat.isAvailable() || crosstie$isShaderEnabled()) {
            return drawWithMinecraftTessellator();
        } else {
            return drawVertexArray();
        }
    }

    /**
     * @author Suzumiya
     * @reason AngelicaやOptiFine+Shader環境で、MCのTessellatorに頂点データを流し込む。
     *
     * <p>ただし、OptiFine+Shader環境下では、GL_TRIANGLE_STRIP などのストリップ系プリミティブを
     * そのままMC Tessellator（のOptiFineフック）に流し込むと、インデックス再構築が壊れて
     * 描画が完全に消失する（透明化する）問題が発生する。
     * これを防ぐため、GL_TRIANGLE_STRIP / GL_TRIANGLE_FAN / GL_LINE_STRIP などの
     * ストリップ形式の描画モードを、MC Tessellatorにはそれぞれ GL_TRIANGLES / GL_LINES
     * に展開・変換して流し込むことで安全に描画できるようにする。</p>
     *
     * <p>また、OptiFine+Shader環境では、GL_TEXTURE_2D が無効化された状態で
     * MC Tessellatorに描画を渡すと、G-Bufferパスからジオメトリが破棄されて透明になってしまう。
     * このため、一時的に GL_TEXTURE_2D を有効化し、テクスチャ座標(UV)を持たない頂点に対しても
     * ダミーのUV(0,0)を割り当てて mc.addVertexWithUV() を呼び出すことで、
     * 通常のテクスチャ付きオブジェクトとして認識させて描画を正常化する。</p>
     */
    @Overwrite
    private int drawWithMinecraftTessellator() {
        Tessellator mc = Tessellator.instance;

        // Shader有効かつGL_TEXTURE_2Dが無効の場合、
        // 一時的にGL_TEXTURE_2Dを有効化してG-Bufferパスに正常にジオメトリを通す
        boolean textureDisabledByScript = false;
        int previousTexture = 0;
        if (crosstie$isShaderEnabled() && !GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            textureDisabledByScript = true;
            
            if (crosstie$whiteTexture == -1) {
                crosstie$whiteTexture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, crosstie$whiteTexture);
                java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(4);
                buffer.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
                buffer.flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, crosstie$whiteTexture);
            }
        }

        if (this.drawMode == GL11.GL_TRIANGLE_STRIP && this.vertexCount >= 3) {
            mc.startDrawing(GL11.GL_TRIANGLES);
            for (int i = 0; i < this.vertexCount - 2; i++) {
                int idx0, idx1, idx2;
                if ((i & 1) == 0) {
                    idx0 = i;
                    idx1 = i + 1;
                    idx2 = i + 2;
                } else {
                    idx0 = i + 1;
                    idx1 = i;
                    idx2 = i + 2;
                }
                crosstie$addVertexToMc(mc, idx0);
                crosstie$addVertexToMc(mc, idx1);
                crosstie$addVertexToMc(mc, idx2);
            }
        } else if (this.drawMode == GL11.GL_TRIANGLE_FAN && this.vertexCount >= 3) {
            mc.startDrawing(GL11.GL_TRIANGLES);
            for (int i = 0; i < this.vertexCount - 2; i++) {
                crosstie$addVertexToMc(mc, 0);
                crosstie$addVertexToMc(mc, i + 1);
                crosstie$addVertexToMc(mc, i + 2);
            }
        } else if (this.drawMode == GL11.GL_LINE_STRIP && this.vertexCount >= 2) {
            mc.startDrawing(GL11.GL_LINES);
            for (int i = 0; i < this.vertexCount - 1; i++) {
                crosstie$addVertexToMc(mc, i);
                crosstie$addVertexToMc(mc, i + 1);
            }
        } else {
            mc.startDrawing(this.drawMode);
            for (int i = 0; i < this.vertexCount; i++) {
                crosstie$addVertexToMc(mc, i);
            }
        }

        int result = mc.draw();

        // 状態を復元する
        if (textureDisabledByScript) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        // バッファサイズの縮小（メモリ節約）
        if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize >> 3)) {
            this.rawBufferSize = 0x10000;
            this.rawBuffer = new int[this.rawBufferSize];
        }

        this.reset();
        return result;
    }

    private void crosstie$addVertexToMc(Tessellator mc, int i) {
        int base = i * 8;

        // 法線を設定
        if (this.hasNormals) {
            int n = this.rawBuffer[base + 6];
            float nx = ((byte) (n & 0xFF)) / 127.0f;
            float ny = ((byte) ((n >> 8) & 0xFF)) / 127.0f;
            float nz = ((byte) ((n >> 16) & 0xFF)) / 127.0f;
            mc.setNormal(nx, ny, nz);
        } else if (crosstie$isShaderEnabled()) {
            mc.setNormal(0.0F, 1.0F, 0.0F);
        }

        // ブライトネスを設定
        if (this.hasBrightness) {
            mc.setBrightness(this.rawBuffer[base + 7]);
        }

        // 色を設定
        if (this.hasColor) {
            int c = this.rawBuffer[base + 5];
            int r, g, b, a;
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                r = c & 0xFF;
                g = (c >> 8) & 0xFF;
                b = (c >> 16) & 0xFF;
                a = (c >> 24) & 0xFF;
            } else {
                r = (c >> 24) & 0xFF;
                g = (c >> 16) & 0xFF;
                b = (c >> 8) & 0xFF;
                a = c & 0xFF;
            }
            mc.setColorRGBA(r, g, b, a);
        }

        // 頂点座標を取得
        float x = Float.intBitsToFloat(this.rawBuffer[base]);
        float y = Float.intBitsToFloat(this.rawBuffer[base + 1]);
        float z = Float.intBitsToFloat(this.rawBuffer[base + 2]);

        // テクスチャ座標付きで頂点を追加
        if (this.hasTexture) {
            float u = Float.intBitsToFloat(this.rawBuffer[base + 3]);
            float v = Float.intBitsToFloat(this.rawBuffer[base + 4]);
            mc.addVertexWithUV(x, y, z, u, v);
        } else {
            if (crosstie$isShaderEnabled()) {
                // Shader有効時はGL_TEXTURE_2D無効+UVなしだと描画が消えるため、
                // ダミーのUV(0,0)を付与してマインクラフトTessellator経由でG-Bufferに通す
                mc.addVertexWithUV(x, y, z, 0.0F, 0.0F);
            } else {
                mc.addVertex(x, y, z);
            }
        }
    }
}
