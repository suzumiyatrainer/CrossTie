package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import net.minecraft.client.renderer.Tessellator;
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
        if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize << 3)) {
            this.rawBufferSize = 0x10000;
            this.rawBuffer = new int[this.rawBufferSize];
        }

        this.reset();
        return result;
    }
}
