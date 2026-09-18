package net.suzumiya.crosstie.mixins.optifine;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.nio.ByteOrder;

@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorOptiFineShaderMixin {
    @Unique
    private static int crosstie$whiteTexture = -1;

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
    @Shadow private int drawVertexArray() { return 0; }

    @Unique
    private static volatile java.lang.reflect.Field crosstie$shaderPackLoadedField = null;
    @Unique
    private static volatile boolean crosstie$shaderFieldLookupDone = false;

    private boolean crosstie$isShaderEnabled() {
        if (jp.ngt.ngtlib.util.NGTUtilClient.usingShader()) {
            return true;
        }
        if (!crosstie$shaderFieldLookupDone) {
            crosstie$shaderFieldLookupDone = true;
            try {
                Class<?> clazz = Class.forName("shadersmod.client.Shaders");
                java.lang.reflect.Field field = clazz.getDeclaredField("shaderPackLoaded");
                field.setAccessible(true);
                crosstie$shaderPackLoadedField = field;
            } catch (Throwable ignored) {}
        }
        if (crosstie$shaderPackLoadedField != null) {
            try {
                return crosstie$shaderPackLoadedField.getBoolean(null);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
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

        if (crosstie$isShaderEnabled()) {
            return drawWithMinecraftTessellator();
        } else {
            return drawVertexArray();
        }
    }

    @Unique
    private int drawWithMinecraftTessellator() {
        Tessellator mc = Tessellator.instance;

        boolean textureDisabledByScript = false;
        int previousTexture = 0;
        if (!GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
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

        if (textureDisabledByScript) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize >> 3)) {
            this.rawBufferSize = 0x10000;
            this.rawBuffer = new int[this.rawBufferSize];
        }

        this.reset();
        return result;
    }

    @Unique
    private void crosstie$addVertexToMc(Tessellator mc, int i) {
        int base = i * 8;
        if (this.hasNormals) {
            int n = this.rawBuffer[base + 6];
            float nx = ((byte) (n & 0xFF)) / 127.0f;
            float ny = ((byte) ((n >> 8) & 0xFF)) / 127.0f;
            float nz = ((byte) ((n >> 16) & 0xFF)) / 127.0f;
            mc.setNormal(nx, ny, nz);
        } else {
            mc.setNormal(0.0F, 1.0F, 0.0F);
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
            mc.addVertexWithUV(x, y, z, 0.0F, 0.0F);
        }
    }
}
