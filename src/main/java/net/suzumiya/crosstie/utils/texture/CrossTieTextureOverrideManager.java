package net.suzumiya.crosstie.utils.texture;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;

/**
 * Manages texture overrides for entities. Uses FastUtil to prevent autoboxing
 * overhead in the render loop.
 */
public class CrossTieTextureOverrideManager {

    private static final Int2ObjectMap<Byte2ObjectMap<Object>> OVERRIDES = new Int2ObjectOpenHashMap<>();

    public static int getObjectId(Object obj) {
        if (obj instanceof Entity) {
            return ((Entity) obj).getEntityId();
        }
        return System.identityHashCode(obj);
    }

    /**
     * Sets the texture for a specific material on an entity or tile entity. Early
     * returns if the texture path is identical to the current one to prevent
     * overhead.
     *
     * @param entity The target object (Entity or TileEntity).
     * @param matId  The material ID (0-255).
     * @param path   The texture path.
     */
    public static void setTexture(Object entity, int matId, String path) {
        if (entity == null || path == null)
            return;

        int entityId = getObjectId(entity);
        byte materialId = (byte) matId;

        Byte2ObjectMap<Object> entityMap = OVERRIDES.get(entityId);
        if (entityMap != null) {
            Object current = entityMap.get(materialId);
            // Early return if the path is identical
            if (current instanceof ResourceLocation) {
                if (((ResourceLocation) current).getResourcePath().equals(path)) {
                    return;
                }
            } else if (current instanceof GifTextureRecord) {
                if (((GifTextureRecord) current).getPath().equals(path)) {
                    return;
                }
            }
        } else {
            entityMap = new Byte2ObjectOpenHashMap<>();
            OVERRIDES.put(entityId, entityMap);
        }

        // Path changed or first time
        if (path.toLowerCase().endsWith(".gif")) {
            GifTextureRecord record = CrossTieGifTextureManager.getOrCreate(path);
            entityMap.put(materialId, record);
        } else {
            // Static texture
            ResourceLocation loc = path.contains(":") ? new ResourceLocation(path)
                    : new ResourceLocation("minecraft", path);
            entityMap.put(materialId, loc);
        }
    }

    /**
     * Resets the texture override for a specific material on an entity, reverting
     * it to the default.
     *
     * @param entity The target object (Entity or TileEntity).
     * @param matId  The material ID (0-255).
     */
    public static void resetTexture(Object entity, int matId) {
        if (entity == null)
            return;

        int entityId = getObjectId(entity);
        byte materialId = (byte) matId;

        Byte2ObjectMap<Object> entityMap = OVERRIDES.get(entityId);
        if (entityMap != null) {
            entityMap.remove(materialId);
            if (entityMap.isEmpty()) {
                OVERRIDES.remove(entityId);
            }
        }
    }

    /**
     * Gets the texture override for a specific entity and material. Returns null if
     * no override exists.
     *
     * @param entityId   The entity ID.
     * @param materialId The material ID.
     * @return The overridden texture (ResourceLocation or GifTextureRecord), or
     *         null.
     */
    public static Object getOverride(int entityId, byte materialId) {
        Byte2ObjectMap<Object> entityMap = OVERRIDES.get(entityId);
        if (entityMap != null) {
            return entityMap.get(materialId);
        }
        return null;
    }

    /**
     * Clears all overrides. Used for garbage collection (e.g. on reload).
     */
    public static void clearAll() {
        OVERRIDES.clear();
        CrossTieGifTextureManager.clearAll();
    }

    public static void bindTexture(Object entity, int matId, String defaultPath) {
        Object override = null;
        if (entity != null) {
            override = getOverride(getObjectId(entity), (byte) matId);
        }

        boolean handled = false;
        if (override instanceof ResourceLocation) {
            ResourceLocation loc = (ResourceLocation) override;
            if (loc.getResourcePath().toLowerCase().endsWith(".gif")) {
                GifTextureRecord record = CrossTieGifTextureManager.getOrCreate(loc);
                if (record != null) {
                    int tex = record.getCurrentTextureId(System.currentTimeMillis());
                    if (tex != 0) {
                        CrossTieGifTextureManager.bindTexture(tex);
                        handled = true;
                    }
                }
            } else {
                CrossTieGifTextureManager.unbindTexture(); // ★追加: GIF→静止画に戻す際のキャッシュリセット
                jp.ngt.ngtlib.util.NGTUtilClient.bindTexture(loc);
                handled = true;
            }
        } else if (override instanceof GifTextureRecord) {
            int tex = ((GifTextureRecord) override).getCurrentTextureId(System.currentTimeMillis());
            if (tex != 0) {
                CrossTieGifTextureManager.bindTexture(tex);
                handled = true;
            }
        }

        if (!handled && defaultPath != null && !defaultPath.isEmpty()) {
            ResourceLocation targetLoc = defaultPath.contains(":") ? new ResourceLocation(defaultPath)
                    : new ResourceLocation("minecraft", defaultPath);
            if (targetLoc.getResourcePath().toLowerCase().endsWith(".gif")) {
                GifTextureRecord record = CrossTieGifTextureManager.getOrCreate(targetLoc);
                if (record != null) {
                    int tex = record.getCurrentTextureId(System.currentTimeMillis());
                    if (tex != 0) {
                        CrossTieGifTextureManager.bindTexture(tex);
                        return;
                    }
                }
            }
            CrossTieGifTextureManager.unbindTexture(); // ★追加: デフォルトへのフォールバック時も同様
            jp.ngt.ngtlib.util.NGTUtilClient.bindTexture(targetLoc);
        }
    }
}
