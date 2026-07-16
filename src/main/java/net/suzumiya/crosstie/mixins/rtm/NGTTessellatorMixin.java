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
     *
     * <p>ただし、OptiFine+Shader環境かつAngelicaなし の場合に
     * {@code GL_TEXTURE_2D} が無効状態でこのメソッドが呼ばれると、
     * OptiFineがMC TessellatorをG-Bufferパスとして横取りし、
     * テクスチャなしジオメトリをdiscardしてワイヤーが完全に消える問題が発生する
     * （{@code RenderBasicWire.js} 等、スクリプトが {@code glDisable(GL_TEXTURE_2D)}
     * してから描画するケースが該当）。
     * その場合は {@code drawVertexArray()} にフォールバックして回避する。
     */
    @Overwrite
    public int draw() {
        if (!this.isDrawing) {
            throw new IllegalStateException("Not tesselating!");
        }
        this.isDrawing = false;

        if (jp.kaiz.kaizpatch.compat.AngelicaCompat.isAvailable() || crosstie$isShaderEnabled()) {
            // OptiFine+Shader環境（Angelicaなし）でGL_TEXTURE_2Dが無効の場合、
            // MC TessellatorをG-Bufferパスに通すとジオメトリがdiscardされるため
            // drawVertexArray()にフォールバックする。
            // （例: RenderBasicWire.jsはglDisable(GL_TEXTURE_2D)後にNGTTessellatorを使う）
            if (crosstie$isShaderEnabled()
                    && !jp.kaiz.kaizpatch.compat.AngelicaCompat.isAvailable()
                    && !GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
                return drawVertexArray();
            }
            return drawWithMinecraftTessellator();
        } else {
            return drawVertexArray();
        }
    }

    /**
     * @author Suzumiya
     * @reason Angelica環境下で巨大な頂点数を一度にTessellatorに流すとバッファ溢れで描画が消えるバグを、分割描画（9600頂点ごと）を行うことで解決する。
     */
    @Overwrite
    private int drawWithMinecraftTessellator() {
        Tessellator mc = Tessellator.instance;

        mc.startDrawing(this.drawMode);

        for (int i = 0; i < this.vertexCount; i++) {
            int base = i * 8;

            // 法線を設定
            if (this.hasNormals) {
                int n = this.rawBuffer[base + 6];
                float nx = ((byte) (n & 0xFF)) / 127.0f;
                float ny = ((byte) ((n >> 8) & 0xFF)) / 127.0f;
                float nz = ((byte) ((n >> 16) & 0xFF)) / 127.0f;
                mc.setNormal(nx, ny, nz);
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
                mc.addVertex(x, y, z);
            }
        }

        int result = mc.draw();

        // バッファサイズの縮小（メモリ節約）
        if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize >> 3)) {
            this.rawBufferSize = 0x10000;
            this.rawBuffer = new int[this.rawBufferSize];
        }

        this.reset();
        return result;
    }
}
