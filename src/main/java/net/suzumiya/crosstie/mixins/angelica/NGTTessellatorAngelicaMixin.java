package net.suzumiya.crosstie.mixins.angelica;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import net.minecraft.client.renderer.Tessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.nio.ByteOrder;

@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorAngelicaMixin {

    @Shadow private int vertexCount;
    @Shadow private int drawMode;
    @Shadow private boolean hasNormals;
    @Shadow private boolean hasBrightness;
    @Shadow private boolean hasColor;
    @Shadow private boolean hasTexture;
    @Shadow private int[] rawBuffer;
    @Shadow private int rawBufferSize;
    @Shadow private int rawBufferIndex;
    @Shadow public abstract void reset();
    @Shadow private boolean isDrawing;

    /**
     * @author Suzumiya
     * @reason Angelica環境で、MCのTessellator (TessellatorManager) に頂点データを流し込む。
     */
    @Overwrite
    public int draw() {
        if (!this.isDrawing) {
            throw new IllegalStateException("Not tesselating!");
        }
        this.isDrawing = false;
        return crosstie$drawWithTessellatorManager();
    }

    private int crosstie$drawWithTessellatorManager() {
        Tessellator mc = TessellatorManager.get();

        if (this.drawMode == GL11.GL_TRIANGLE_STRIP && this.vertexCount >= 3) {
            mc.startDrawing(GL11.GL_TRIANGLES);
            for (int i = 0; i < this.vertexCount - 2; i++) {
                int idx0, idx1, idx2;
                if ((i & 1) == 0) {
                    idx0 = i; idx1 = i + 1; idx2 = i + 2;
                } else {
                    idx0 = i + 1; idx1 = i; idx2 = i + 2;
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

        if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize >> 3)) {
            this.rawBufferSize = 0x10000;
            this.rawBuffer = new int[this.rawBufferSize];
        }

        this.reset();
        return result;
    }

    private void crosstie$addVertexToMc(Tessellator mc, int i) {
        int base = i * 8;
        if (this.hasNormals) {
            int n = this.rawBuffer[base + 6];
            float nx = ((byte) (n & 0xFF)) / 127.0f;
            float ny = ((byte) ((n >> 8) & 0xFF)) / 127.0f;
            float nz = ((byte) ((n >> 16) & 0xFF)) / 127.0f;
            mc.setNormal(nx, ny, nz);
        }
        if (this.hasBrightness) {
            mc.setBrightness(this.rawBuffer[base + 7]);
        }
        if (this.hasColor) {
            int c = this.rawBuffer[base + 5];
            int r, g, b, a;
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                r = c & 0xFF; g = (c >> 8) & 0xFF; b = (c >> 16) & 0xFF; a = (c >> 24) & 0xFF;
            } else {
                r = (c >> 24) & 0xFF; g = (c >> 16) & 0xFF; b = (c >> 8) & 0xFF; a = c & 0xFF;
            }
            mc.setColorRGBA(r, g, b, a);
        }
        float x = Float.intBitsToFloat(this.rawBuffer[base]);
        float y = Float.intBitsToFloat(this.rawBuffer[base + 1]);
        float z = Float.intBitsToFloat(this.rawBuffer[base + 2]);
        if (this.hasTexture) {
            float u = Float.intBitsToFloat(this.rawBuffer[base + 3]);
            float v = Float.intBitsToFloat(this.rawBuffer[base + 4]);
            mc.addVertexWithUV(x, y, z, u, v);
        } else {
            mc.addVertex(x, y, z);
        }
    }
}
