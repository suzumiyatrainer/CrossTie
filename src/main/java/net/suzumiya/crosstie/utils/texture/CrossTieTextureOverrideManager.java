package net.suzumiya.crosstie.utils.texture;

import java.util.Arrays;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * Manages texture overrides for entities. Uses primitive array-based lookup to
 * prevent autoboxing overhead in the render loop without external dependencies
 * (works without GTNHLib/fastutil).
 */
public class CrossTieTextureOverrideManager {

    private static final IntObjectMap<MaterialOverrideTable> OVERRIDES = new IntObjectMap<>(64);

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
        int matIndex = matId & 0xFF;

        MaterialOverrideTable entityMap = OVERRIDES.get(entityId);
        if (entityMap != null) {
            Object current = entityMap.get(matIndex);
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
            entityMap = new MaterialOverrideTable();
            OVERRIDES.put(entityId, entityMap);
        }

        // Path changed or first time
        if (path.toLowerCase().endsWith(".gif")) {
            GifTextureRecord record = CrossTieGifTextureManager.getOrCreate(path);
            entityMap.put(matIndex, record);
        } else {
            // Static texture
            ResourceLocation loc = path.contains(":") ? new ResourceLocation(path)
                    : new ResourceLocation("minecraft", path);
            entityMap.put(matIndex, loc);
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
        int matIndex = matId & 0xFF;

        MaterialOverrideTable entityMap = OVERRIDES.get(entityId);
        if (entityMap != null) {
            entityMap.remove(matIndex);
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
        if (OVERRIDES.isEmpty()) {
            return null;
        }
        MaterialOverrideTable entityMap = OVERRIDES.get(entityId);
        if (entityMap != null) {
            return entityMap.get(materialId & 0xFF);
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

    /**
     * Direct array-indexed table for material IDs (0..255).
     */
    private static final class MaterialOverrideTable {
        private Object[] slots = new Object[16];
        private int count = 0;

        Object get(int matIndex) {
            return matIndex < slots.length ? slots[matIndex] : null;
        }

        void put(int matIndex, Object value) {
            if (matIndex >= slots.length) {
                int newLen = Math.min(256, Math.max(slots.length << 1, Integer.highestOneBit(matIndex) << 1));
                slots = Arrays.copyOf(slots, newLen);
            }
            if (slots[matIndex] == null && value != null) {
                count++;
            } else if (slots[matIndex] != null && value == null) {
                count--;
            }
            slots[matIndex] = value;
        }

        void remove(int matIndex) {
            if (matIndex < slots.length && slots[matIndex] != null) {
                slots[matIndex] = null;
                count--;
            }
        }

        boolean isEmpty() {
            return count == 0;
        }
    }

    /**
     * Lightweight open-addressing int-to-object map (no autoboxing, no external
     * library dependencies).
     */
    private static final class IntObjectMap<V> {
        private int[] keys;
        private Object[] values;
        private boolean[] used;
        private int mask;
        private int size;
        private int maxFill;

        IntObjectMap(int expected) {
            int cap = 16;
            while (cap < expected * 2) {
                cap <<= 1;
            }
            this.keys = new int[cap];
            this.values = new Object[cap];
            this.used = new boolean[cap];
            this.mask = cap - 1;
            this.maxFill = (cap * 3) >>> 2;
        }

        boolean isEmpty() {
            return size == 0;
        }

        @SuppressWarnings("unchecked")
        V get(int key) {
            if (size == 0) {
                return null;
            }
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) {
                    return (V) values[idx];
                }
                idx = (idx + 1) & mask;
            }
            return null;
        }

        void put(int key, V value) {
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) {
                    values[idx] = value;
                    return;
                }
                idx = (idx + 1) & mask;
            }
            used[idx] = true;
            keys[idx] = key;
            values[idx] = value;
            if (++size >= maxFill) {
                rehash(keys.length << 1);
            }
        }

        void remove(int key) {
            if (size == 0) {
                return;
            }
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) {
                    size--;
                    shiftKeys(idx);
                    return;
                }
                idx = (idx + 1) & mask;
            }
        }

        private void shiftKeys(int pos) {
            int last;
            int slot;
            for (;;) {
                pos = ((last = pos) + 1) & mask;
                for (;;) {
                    if (!used[pos]) {
                        used[last] = false;
                        values[last] = null;
                        return;
                    }
                    slot = mix(keys[pos]) & mask;
                    if (last <= pos ? (last >= slot || slot > pos) : (last >= slot && slot > pos)) {
                        break;
                    }
                    pos = (pos + 1) & mask;
                }
                keys[last] = keys[pos];
                values[last] = values[pos];
            }
        }

        void clear() {
            if (size == 0) {
                return;
            }
            Arrays.fill(used, false);
            Arrays.fill(values, null);
            size = 0;
        }

        private void rehash(int newCap) {
            int[] oldKeys = this.keys;
            Object[] oldValues = this.values;
            boolean[] oldUsed = this.used;

            this.keys = new int[newCap];
            this.values = new Object[newCap];
            this.used = new boolean[newCap];
            this.mask = newCap - 1;
            this.maxFill = (newCap * 3) >>> 2;

            for (int i = 0; i < oldKeys.length; i++) {
                if (oldUsed[i]) {
                    int idx = mix(oldKeys[i]) & mask;
                    while (used[idx]) {
                        idx = (idx + 1) & mask;
                    }
                    used[idx] = true;
                    keys[idx] = oldKeys[i];
                    values[idx] = oldValues[i];
                }
            }
        }

        private static int mix(int x) {
            int h = x * -1640531527;
            return h ^ (h >>> 16);
        }
    }
}
