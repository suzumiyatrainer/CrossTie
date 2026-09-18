package net.suzumiya.crosstie.utils.texture;

import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CrossTieGifTextureManager {

    private static final Map<String, GifTextureRecord> CACHE = new ConcurrentHashMap<>();
    private static Method angelicaBindTextureMethod = null;
    private static boolean angelicaChecked = false;
    private static boolean angelicaAvailable = false;
    public static boolean gifWasBound = false;

    public static GifTextureRecord getOrCreate(ResourceLocation location) {
        if (location == null)
            return null;
        return getOrCreate(location.toString(), location);
    }

    public static GifTextureRecord getOrCreate(String path) {
        if (path == null || path.isEmpty())
            return null;
        ResourceLocation loc = path.contains(":") ? new ResourceLocation(path)
                : new ResourceLocation("minecraft", path);
        return getOrCreate(path, loc);
    }

    private static GifTextureRecord getOrCreate(String key, ResourceLocation loc) {
        return CACHE.computeIfAbsent(key, k -> new GifTextureRecord(loc));
    }

    private static void ensureAngelicaChecked() {
        if (!angelicaChecked) {
            try {
                Class<?> glsmClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                angelicaBindTextureMethod = glsmClass.getMethod("bindTexture", int.class);
                angelicaAvailable = true;
            } catch (Throwable ignored) {
                angelicaAvailable = false;
            }
            angelicaChecked = true;
        }
    }

    /**
     * すべてのGLテクスチャバインドはここを唯一の経路として通す。 Angelicaが存在する場合はGLStateManager経由でバインドし、
     * Angelica自身の内部キャッシュを常に正しい状態に保つ。 (これを bindTexture 側だけでなく unbindTexture 側でも
     * 必ず対称に呼ぶことで、キャッシュ不整合による 「無関係なブロック/エンティティに白黒GIFフレームが 残り続けて世界全体がチラつく」バグを防ぐ)
     */
    private static void doBind(int textureId) {
        ensureAngelicaChecked();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (angelicaAvailable && angelicaBindTextureMethod != null) {
            try {
                angelicaBindTextureMethod.invoke(null, textureId);
                return;
            } catch (Throwable ignored) {
                // フォールバックして下のGL11直接呼び出しへ
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    /**
     * GIFアニメーションフレームのテクスチャIDをバインドする。
     */
    public static void bindTexture(int textureId) {
        if (textureId <= 0)
            return;
        doBind(textureId);
        gifWasBound = true;
    }

    /**
     * GIFフレームバインド後の状態をリセットする。 bindTexture() と完全に対称な doBind() を通すことで、
     * Angelica等のキャッシュが「まだGIFがバインドされている」と 誤認したまま残ることがないようにする。 GLにおいて 0
     * は常に合法な「未バインド」を表す値。
     */
    public static void unbindTexture() {
        if (!gifWasBound)
            return;
        gifWasBound = false;
        doBind(0);
    }

    public static void clearAll() {
        for (GifTextureRecord record : CACHE.values()) {
            record.deleteTextures();
        }
        CACHE.clear();
    }
}