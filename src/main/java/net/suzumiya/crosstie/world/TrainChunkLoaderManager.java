package net.suzumiya.crosstie.world;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.OrderedLoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.event.world.WorldEvent;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.CrossTieConfig;

/**
 * RTM 列車専用の極軽量・最小限チャンクローダーマネージャー。
 *
 * <p>
 * RTM 既存の State_ChunkLoader（広域ロード）とは独立して動作し、
 * 各車両（最大25m列車）の前後左右+3ブロックの範囲のみをピンポイントで forceChunk に維持する。
 * これにより、8両以上の長編成における後方車両・台車のアンロードによる消失（台車消失バグ）や、
 * ドア開閉・チャイム・ホームドアとの同期遅延（1tick以上のズレ）を解消する。
 * </p>
 */
public class TrainChunkLoaderManager implements OrderedLoadingCallback {

    public static final TrainChunkLoaderManager INSTANCE = new TrainChunkLoaderManager();

    private final Map<Integer, DimensionTracker> trackers = new ConcurrentHashMap<>();

    private TrainChunkLoaderManager() {
    }

    /**
     * 車両の更新時に呼び出し、最小限の必要チャンクを forceChunk 状態に維持する。
     */
    public void onTrainUpdate(EntityTrainBase train) {
        if (!CrossTieConfig.enableTrainMinimalChunkLoader) {
            return;
        }
        if (train == null || train.isDead || train.worldObj == null || train.worldObj.isRemote) {
            return;
        }

        DimensionTracker tracker = getTracker(train.worldObj);
        tracker.updateTrain(train);
    }

    /**
     * 車両の撤去時・死亡時に呼び出し、チャンクの参照カウントを解放する。
     */
    public void onTrainRemoved(EntityTrainBase train) {
        if (train == null || train.worldObj == null || train.worldObj.isRemote) {
            return;
        }

        DimensionTracker tracker = trackers.get(train.worldObj.provider.dimensionId);
        if (tracker != null) {
            tracker.removeTrain(train);
        }
    }

    private DimensionTracker getTracker(World world) {
        int dim = world.provider.dimensionId;
        DimensionTracker tracker = trackers.get(dim);
        if (tracker == null) {
            synchronized (trackers) {
                tracker = trackers.get(dim);
                if (tracker == null) {
                    tracker = new DimensionTracker(world);
                    trackers.put(dim, tracker);
                }
            }
        }
        return tracker;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && !event.world.isRemote) {
            int dim = event.world.provider.dimensionId;
            DimensionTracker tracker = trackers.remove(dim);
            if (tracker != null) {
                tracker.clear();
            }
        }
    }

    /**
     * 全ディメンションのチケットと状態を全解放する（Config無効化時やサーバー停止時用）。
     */
    public void clearAll() {
        for (DimensionTracker tracker : trackers.values()) {
            tracker.clear();
        }
        trackers.clear();
    }

    // ---- ForgeChunkManager Callbacks ---- //

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        // サーバー起動時・ワールド読み込み時、古いチケットは解放する（列車スポーン/ロード時に動的に再確保）
        for (Ticket ticket : tickets) {
            try {
                ForgeChunkManager.releaseTicket(ticket);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> tickets, World world, int maxTicketCount) {
        for (Ticket ticket : tickets) {
            try {
                ForgeChunkManager.releaseTicket(ticket);
            } catch (Throwable ignored) {
            }
        }
        return Collections.emptyList();
    }

    // ---- 車両範囲（前後左右+3ブロック）のチャンク計算 ---- //

    /**
     * 最大25m列車の前後左右+3ブロック、および台車位置±3ブロックを内包する最小限のチャンク座標集合を計算する。
     */
    public static Set<ChunkCoordIntPair> calculateTrainChunks(EntityTrainBase train) {
        Set<ChunkCoordIntPair> result = new HashSet<>(4);
        if (train == null || train.isDead) {
            return result;
        }

        // 車両の寸法推定（モデル設定から取得、無ければ最大25m標準値）
        float halfLength = 12.5f; // 最大25mの半分
        float halfWidth = 1.5f;   // 車幅3mの半分

        try {
            if (train.getModelSet() != null && train.getModelSet().getConfig() != null) {
                float[][] bogiePos = train.getModelSet().getConfig().getBogiePos();
                if (bogiePos != null && bogiePos.length >= 2) {
                    float lenBogie = Math.abs(bogiePos[0][2] - bogiePos[1][2]);
                    float estimatedLength = Math.max(lenBogie + 8.0f, 20.0f);
                    halfLength = Math.min(estimatedLength * 0.5f, 13.0f);
                }
            }
        } catch (Throwable ignored) {
        }

        // 前後左右 +3ブロックのマージン
        double extraFrontBack = halfLength + 3.0;
        double extraSide = halfWidth + 3.0;

        double rad = Math.toRadians(train.rotationYaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        // 前後・左右の相対ベクトル (Minecraft の yaw: 前方 = (-sin, cos), 右 = (cos, sin))
        double dxF = -sin * extraFrontBack;
        double dzF = cos * extraFrontBack;
        double dxS = cos * extraSide;
        double dzS = sin * extraSide;

        double x = train.posX;
        double z = train.posZ;

        // 車体の4隅（マージン+3ブロック含む）
        double p1x = x + dxF + dxS;
        double p1z = z + dzF + dzS;
        double p2x = x + dxF - dxS;
        double p2z = z + dzF - dzS;
        double p3x = x - dxF + dxS;
        double p3z = z - dzF + dzS;
        double p4x = x - dxF - dxS;
        double p4z = z - dzF - dzS;

        double minX = Math.min(Math.min(p1x, p2x), Math.min(p3x, p4x));
        double maxX = Math.max(Math.max(p1x, p2x), Math.max(p3x, p4x));
        double minZ = Math.min(Math.min(p1z, p2z), Math.min(p3z, p4z));
        double maxZ = Math.max(Math.max(p1z, p2z), Math.max(p3z, p4z));

        // 台車（前台車・後台車）の存在位置±3ブロックも確実に包含
        Set<ChunkCoordIntPair> coreChunks = new HashSet<>(2);
        for (int i = 0; i < 2; i++) {
            EntityBogie bogie = train.getBogie(i);
            if (bogie != null && !bogie.isDead) {
                minX = Math.min(minX, bogie.posX - 3.0);
                maxX = Math.max(maxX, bogie.posX + 3.0);
                minZ = Math.min(minZ, bogie.posZ - 3.0);
                maxZ = Math.max(maxZ, bogie.posZ + 3.0);

                // 台車が乗っているレールのコア位置のチャンクも保持する。
                // コア TileEntity はチャンクがロードされている間のみ tick され、在線状態(WebCTC)が更新されるため。
                TileEntityLargeRailCore core = getCurrentRailCore(bogie);
                if (core != null && !core.isInvalid()) {
                    coreChunks.add(new ChunkCoordIntPair(core.xCoord >> 4, core.zCoord >> 4));
                }
            }
        }

        int minChunkX = MathHelper.floor_double(minX) >> 4;
        int maxChunkX = MathHelper.floor_double(maxX) >> 4;
        int minChunkZ = MathHelper.floor_double(minZ) >> 4;
        int maxChunkZ = MathHelper.floor_double(maxZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                result.add(new ChunkCoordIntPair(cx, cz));
            }
        }
        result.addAll(coreChunks);

        return result;
    }

    private static java.lang.reflect.Field currentRailObjField;
    private static boolean currentRailObjFieldFailed = false;

    /**
     * EntityBogie#currentRailObj (private) をリフレクションで取得する。
     */
    private static TileEntityLargeRailCore getCurrentRailCore(EntityBogie bogie) {
        if (currentRailObjFieldFailed) {
            return null;
        }
        try {
            if (currentRailObjField == null) {
                java.lang.reflect.Field f = EntityBogie.class.getDeclaredField("currentRailObj");
                f.setAccessible(true);
                currentRailObjField = f;
            }
            return (TileEntityLargeRailCore) currentRailObjField.get(bogie);
        } catch (Throwable t) {
            currentRailObjFieldFailed = true;
            return null;
        }
    }

    // ---- ディメンション単位のトラッカー ---- //

    private static class LastPos {
        double x, z;
        float yaw;

        LastPos(double x, double z, float yaw) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
        }
    }

    private static class DimensionTracker {
        final World world;
        final List<Ticket> tickets = new ArrayList<>();
        final Map<ChunkCoordIntPair, Integer> chunkRefCounts = new HashMap<>();
        final Map<ChunkCoordIntPair, Ticket> chunkToTicket = new HashMap<>();
        final Map<EntityTrainBase, Set<ChunkCoordIntPair>> trainChunks = Collections.synchronizedMap(new WeakHashMap<>());
        final Map<EntityTrainBase, LastPos> trainLastPositions = Collections.synchronizedMap(new WeakHashMap<>());

        DimensionTracker(World world) {
            this.world = world;
        }

        synchronized void updateTrain(EntityTrainBase train) {
            if (!CrossTieConfig.enableTrainMinimalChunkLoader || train == null || train.isDead || train.worldObj != this.world) {
                removeTrain(train);
                return;
            }

            // 電車が置かれた時（ticksExisted）を基準に5秒（100 ticks）ごとの定期判定
            boolean isPeriodicCheck = (train.ticksExisted % 100 == 0);

            if (!isPeriodicCheck) {
                // 通常Tick: 微小移動の判定（停止中や微動時は計算をスキップして負荷をゼロにする）
                LastPos last = trainLastPositions.get(train);
                double x = train.posX;
                double z = train.posZ;
                float yaw = train.rotationYaw;

                if (last != null && Math.abs(x - last.x) < 0.25 && Math.abs(z - last.z) < 0.25 && Math.abs(yaw - last.yaw) < 1.0f) {
                    return;
                }
            }

            // 5秒ごとの定期チェック、または移動発生時:
            // 最新の電車位置に基づき、現在本当に電車が存在するチャンク集合を再計算
            Set<ChunkCoordIntPair> newChunks = calculateTrainChunks(train);
            Set<ChunkCoordIntPair> oldChunks = trainChunks.get(train);

            // 差分を適用（もう電車が存在しない不要なチャンクは即座に unforceChunk で解放）
            if (oldChunks == null || !oldChunks.equals(newChunks)) {
                applyChunkDiff(oldChunks, newChunks);
                trainChunks.put(train, newChunks);
            }

            LastPos last = trainLastPositions.get(train);
            if (last != null) {
                last.x = train.posX;
                last.z = train.posZ;
                last.yaw = train.rotationYaw;
            } else {
                trainLastPositions.put(train, new LastPos(train.posX, train.posZ, train.rotationYaw));
            }

            // 5秒ごとの定期チェック時のみ、トラッカー内の整合性検証（死亡した電車の残留排除など）を実施
            if (isPeriodicCheck) {
                validateAndCleanTracker();
            }
        }

        /**
         * 5秒ごとの定期検証:
         * トラッカー内に登録された電車の生存状態を確認し、死亡した電車やワールド外の電車の不要チャンクを解放する。
         */
        private void validateAndCleanTracker() {
            synchronized (trainChunks) {
                Iterator<Map.Entry<EntityTrainBase, Set<ChunkCoordIntPair>>> it = trainChunks.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<EntityTrainBase, Set<ChunkCoordIntPair>> entry = it.next();
                    EntityTrainBase t = entry.getKey();
                    if (t == null || t.isDead || t.worldObj != this.world) {
                        Set<ChunkCoordIntPair> deadChunks = entry.getValue();
                        it.remove();
                        trainLastPositions.remove(t);
                        if (deadChunks != null) {
                            for (ChunkCoordIntPair chunk : deadChunks) {
                                decrementChunk(chunk);
                            }
                        }
                    }
                }
            }
        }

        synchronized void removeTrain(EntityTrainBase train) {
            Set<ChunkCoordIntPair> oldChunks = trainChunks.remove(train);
            trainLastPositions.remove(train);
            if (oldChunks != null) {
                applyChunkDiff(oldChunks, Collections.emptySet());
            }
        }

        private void applyChunkDiff(Set<ChunkCoordIntPair> oldChunks, Set<ChunkCoordIntPair> newChunks) {
            if (oldChunks != null) {
                for (ChunkCoordIntPair chunk : oldChunks) {
                    if (!newChunks.contains(chunk)) {
                        decrementChunk(chunk);
                    }
                }
            }
            for (ChunkCoordIntPair chunk : newChunks) {
                if (oldChunks == null || !oldChunks.contains(chunk)) {
                    incrementChunk(chunk);
                }
            }
        }

        private void incrementChunk(ChunkCoordIntPair chunk) {
            int count = chunkRefCounts.getOrDefault(chunk, 0);
            chunkRefCounts.put(chunk, count + 1);
            if (count == 0) {
                Ticket ticket = getOrCreateAvailableTicket();
                if (ticket != null) {
                    try {
                        ForgeChunkManager.forceChunk(ticket, chunk);
                        chunkToTicket.put(chunk, ticket);
                    } catch (Throwable t) {
                        CrossTie.LOGGER.warn("Failed to forceChunk {}: {}", chunk, t.getMessage());
                    }
                }
            }
        }

        private void decrementChunk(ChunkCoordIntPair chunk) {
            Integer count = chunkRefCounts.get(chunk);
            if (count == null) {
                return;
            }
            if (count <= 1) {
                chunkRefCounts.remove(chunk);
                Ticket ticket = chunkToTicket.remove(chunk);
                if (ticket != null) {
                    try {
                        ForgeChunkManager.unforceChunk(ticket, chunk);
                    } catch (Throwable ignored) {
                    }
                    cleanupEmptyTickets();
                }
            } else {
                chunkRefCounts.put(chunk, count - 1);
            }
        }

        private Ticket getOrCreateAvailableTicket() {
            for (Ticket ticket : tickets) {
                if (ticket.getChunkList().size() < ticket.getMaxChunkListDepth()) {
                    return ticket;
                }
            }
            try {
                Ticket ticket = ForgeChunkManager.requestTicket(CrossTie.instance, this.world, Type.NORMAL);
                if (ticket != null) {
                    tickets.add(ticket);
                    return ticket;
                }
            } catch (Throwable t) {
                CrossTie.LOGGER.warn("Failed to request ForgeChunkManager ticket for world {}: {}",
                        world.provider.dimensionId, t.getMessage());
            }
            return null;
        }

        private void cleanupEmptyTickets() {
            if (tickets.size() <= 1) {
                return;
            }
            Iterator<Ticket> it = tickets.iterator();
            while (it.hasNext() && tickets.size() > 1) {
                Ticket t = it.next();
                if (t.getChunkList().isEmpty()) {
                    try {
                        ForgeChunkManager.releaseTicket(t);
                    } catch (Throwable ignored) {
                    }
                    it.remove();
                }
            }
        }

        synchronized void clear() {
            for (Ticket ticket : tickets) {
                try {
                    ForgeChunkManager.releaseTicket(ticket);
                } catch (Throwable ignored) {
                }
            }
            tickets.clear();
            chunkRefCounts.clear();
            chunkToTicket.clear();
            trainChunks.clear();
            trainLastPositions.clear();
        }
    }
}
