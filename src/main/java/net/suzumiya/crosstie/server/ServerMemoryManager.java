package net.suzumiya.crosstie.server;

import com.google.common.cache.Cache;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.util.Map;
import jp.ngt.rtm.modelpack.ModelPackManager;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.CrossTieConfig;

/**
 * サーバー側メモリ最適化マネージャー。
 *
 * <p>
 * 主な機能:
 * <ul>
 *   <li>Forge の休眠チャンクキャッシュ (dormantChunkCache) の保持上限制限および定期クリーンアップ。
 *       アンロードされたチャンクがキャッシュ内に何百個も滞留してヒープおよびネイティブメモリを圧迫するのを防止する。</li>
 *   <li>RTM ModelPackManager 内のサーバー側不要キャッシュ（スクリプト文字列キャッシュ等）の定期トリミング。</li>
 *   <li>ワールドセーブ時（WorldEvent.Save）やメモリ高負荷時の安全なキャッシュ無効化。</li>
 * </ul>
 * </p>
 */
public final class ServerMemoryManager {

    public static final ServerMemoryManager INSTANCE = new ServerMemoryManager();

    private static Field dormantChunkCacheField;
    private static Field dormantChunkCacheSizeField;
    private static Field scriptCacheField;
    private static Field modelFileMapField;
    private static boolean fieldsInitialized = false;

    private int tickCounter = 0;
    // 2分（2400 ticks）ごとにチェック
    private static final int CHECK_INTERVAL_TICKS = 2400;

    private ServerMemoryManager() {
    }

    /**
     * 初期化とイベントリスナーの登録。
     */
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        initReflectionFields();
        applyDormantChunkLimit();
    }

    private static void initReflectionFields() {
        if (fieldsInitialized) {
            return;
        }
        try {
            dormantChunkCacheField = ForgeChunkManager.class.getDeclaredField("dormantChunkCache");
            dormantChunkCacheField.setAccessible(true);
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("Could not access ForgeChunkManager.dormantChunkCache field: {}", t.getMessage());
        }

        try {
            dormantChunkCacheSizeField = ForgeChunkManager.class.getDeclaredField("dormantChunkCacheSize");
            dormantChunkCacheSizeField.setAccessible(true);
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("Could not access ForgeChunkManager.dormantChunkCacheSize field: {}", t.getMessage());
        }

        try {
            scriptCacheField = ModelPackManager.class.getDeclaredField("scriptCache");
            scriptCacheField.setAccessible(true);
        } catch (Throwable ignored) {
        }

        try {
            modelFileMapField = ModelPackManager.class.getDeclaredField("modelFileMap");
            modelFileMapField.setAccessible(true);
        } catch (Throwable ignored) {
        }

        fieldsInitialized = true;
    }

    /**
     * Forge の休眠チャンクキャッシュサイズ上限を設定する。
     */
    public void applyDormantChunkLimit() {
        if (!CrossTieConfig.enableServerMemoryOptimizer || dormantChunkCacheSizeField == null) {
            return;
        }
        try {
            int current = dormantChunkCacheSizeField.getInt(null);
            int target = CrossTieConfig.dormantChunkCacheLimit;
            if (current != target) {
                dormantChunkCacheSizeField.setInt(null, target);
                CrossTie.LOGGER.info("Adjusted ForgeChunkManager.dormantChunkCacheSize: {} -> {}", current, target);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("Failed to set ForgeChunkManager.dormantChunkCacheSize: {}", t.getMessage());
        }
    }

    /**
     * 全ワールドの Forge 休眠チャンクキャッシュを全破棄（invalidateAll）する。
     */
    @SuppressWarnings("unchecked")
    public int clearDormantChunkCache() {
        if (dormantChunkCacheField == null) {
            return 0;
        }
        int clearedCount = 0;
        try {
            Map<World, Cache<Long, Object>> map = (Map<World, Cache<Long, Object>>) dormantChunkCacheField.get(null);
            if (map != null && !map.isEmpty()) {
                for (Cache<Long, Object> cache : map.values()) {
                    if (cache != null) {
                        long size = cache.size();
                        if (size > 0) {
                            clearedCount += (int) size;
                            cache.invalidateAll();
                            cache.cleanUp();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("Failed to clear dormantChunkCache: {}", t.getMessage());
        }
        return clearedCount;
    }

    /**
     * RTMのサーバー側不要キャッシュをトリミングする。
     */
    @SuppressWarnings("rawtypes")
    public void trimRtmCaches() {
        try {
            if (scriptCacheField != null) {
                Map cache = (Map) scriptCacheField.get(ModelPackManager.INSTANCE);
                if (cache != null && cache.size() > 100) {
                    cache.clear();
                }
            }
            if (modelFileMapField != null) {
                Map modelMap = (Map) modelFileMapField.get(ModelPackManager.INSTANCE);
                if (modelMap != null && !modelMap.isEmpty()) {
                    modelMap.clear();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!CrossTieConfig.enableServerMemoryOptimizer) {
            return;
        }
        if (event.world != null && !event.world.isRemote) {
            // ワールドセーブ時に休眠チャンクキャッシュを一掃し、ディスク保存済みチャンクのメモリ残留を防ぐ
            clearDormantChunkCache();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CrossTieConfig.enableServerMemoryOptimizer) {
            return;
        }

        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL_TICKS) {
            tickCounter = 0;
            performPeriodicCheck();
        }
    }

    private void performPeriodicCheck() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;

        double usagePercent = (double) used / (double) max;

        // 定期的に dormant cache と RTM キャッシュを整理
        int cleared = clearDormantChunkCache();
        trimRtmCaches();

        if (usagePercent > 0.85) {
            CrossTie.LOGGER.warn("High heap memory usage detected ({}/{} MB, {:.1f}%). Cleared {} dormant chunks.",
                    used / 1048576L, max / 1048576L, usagePercent * 100.0, cleared);
        } else if (cleared > 0) {
            CrossTie.LOGGER.debug("ServerMemoryManager: Cleared {} dormant chunks. Current heap: {}/{} MB.",
                    cleared, used / 1048576L, max / 1048576L);
        }
    }

    /**
     * サーバー停止時のクリーンアップ。
     */
    public void onServerStopping() {
        clearDormantChunkCache();
        trimRtmCaches();
    }
}
