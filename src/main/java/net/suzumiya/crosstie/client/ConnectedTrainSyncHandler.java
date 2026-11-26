package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.RTMEntity;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * [実験的] 連結車両のめり込み・分離（車間ズレ）防止ハンドラ（クライアント側）。
 *
 * <ol>
 * <li><b>Tick 開始時（{@link TickEvent.Phase#START}）：編成単位バリア同期</b><br>
 * Netty IO スレッドで受信した {@code PacketVehicleMovement} をメインスレッドへ集約し、
 * 同一 {@link Formation} 内のロード済み全車両のパケットが揃うまで最大 1 tick 待機してから
 * 一斉に {@code setPositionAndRotation2} を適用する。これにより、Tick 境界跨ぎによる
 * 編成の前後分割（めり込み・離れ）を防止する。</li>
 * <li><b>Tick 終了時（{@link TickEvent.Phase#END}）：編成内車間距離ソルバー</b><br>
 * 全車両の補間更新完了後、同一 {@link Formation} 内の隣接車両間距離を
 * {@code getDefaultDistanceToConnectedTrain}（およびカーブ折れ角による弦長補正）と比較し、
 * めり込み（距離不足）および分離（距離過多）の双方を、プレイヤー乗車車両を優先アンカーとして
 * 車体および台車ごと補正する。サーバー側で設定が OFF の環境でも有効に機能する。</li>
 * </ol>
 *
 * <p>
 * {@link CrossTieConfig#fixConnectedTrainClipping} が {@code true} の場合のみ有効。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ConnectedTrainSyncHandler {

    private static final double DIV_32 = 1.0D / 32.0D;
    private static final int MAX_QUEUE_SIZE = 8192;

    /** 同一編成の残り車両パケットを待つ最大クライアント Tick 数 */
    private static final int MAX_BARRIER_WAIT_TICKS = 1;

    /** 車間距離補正を発動する最小誤差（m）。微小なジッターを避けるデッドバンド */
    private static final double SPACING_DEADBAND = 0.03D;

    /** 異常値・別チャンクワープ時の誤補正を防ぐ最大許容誤差（m） */
    private static final double MAX_CORRECTABLE_ERROR = 8.0D;

    /** カーブ弦長補正を適用する最大折れ角（度） */
    private static final double MAX_CURVE_ANGLE_DEG = 60.0D;

    private static final ConcurrentLinkedQueue<MovementSnapshot> QUEUE = new ConcurrentLinkedQueue<>();

    /** 同一 tick 内で同一エンティティの最新パケットのみを残すための再利用マップ（クライアントメインスレッド専用） */
    private final Map<Integer, MovementSnapshot> latestByEntity = new LinkedHashMap<>();

    /** 編成単位バリア同期用の保留バッファ（formationId -> PendingFormationBatch） */
    private final Map<Long, PendingFormationBatch> pendingFormations = new HashMap<>();

    /** Phase.END での処理済み編成 ID セット（再利用） */
    private final Set<Long> processedFormations = new HashSet<>();

    /** Phase.END での連続車両セグメント作業用リスト（再利用） */
    private final List<EntityTrainBase> segmentBuffer = new ArrayList<>();

    private long clientTickCounter = 0L;

    private static final class MovementSnapshot {
        final int entityId;
        final int rawX;
        final int rawY;
        final int rawZ;
        final float yaw;
        final float pitch;
        final float roll;
        final float speed;

        MovementSnapshot(int entityId, int rawX, int rawY, int rawZ,
                float yaw, float pitch, float roll, float speed) {
            this.entityId = entityId;
            this.rawX = rawX;
            this.rawY = rawY;
            this.rawZ = rawZ;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.speed = speed;
        }
    }

    private static final class PendingFormationBatch {
        long firstSeenTick;
        final Map<Integer, MovementSnapshot> snapshots = new LinkedHashMap<>();

        PendingFormationBatch(long firstSeenTick) {
            this.firstSeenTick = firstSeenTick;
        }
    }

    /**
     * Netty IO スレッドから受信した移動パケットをスレッドセーフにキューへ積む。
     */
    public static void enqueue(int entityId, int rawX, int rawY, int rawZ,
            float yaw, float pitch, float roll, float speed) {
        if (QUEUE.size() >= MAX_QUEUE_SIZE) {
            QUEUE.poll();
        }
        QUEUE.offer(new MovementSnapshot(entityId, rawX, rawY, rawZ, yaw, pitch, roll, speed));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!CrossTieConfig.fixConnectedTrainClipping) {
            this.clearPendingState();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = (mc != null) ? mc.theWorld : null;
        if (world == null) {
            this.clearPendingState();
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            ++this.clientTickCounter;
            this.flushMovementPackets(world);
        } else if (event.phase == TickEvent.Phase.END) {
            this.solveFormationSpacing(mc, world);
        }
    }

    private void clearPendingState() {
        if (!QUEUE.isEmpty()) {
            QUEUE.clear();
        }
        if (!this.pendingFormations.isEmpty()) {
            this.pendingFormations.clear();
        }
    }

    /**
     * クライアント Tick の先頭（{@code WorldClient#updateEntities()} 実行前）に、
     * 蓄積された車両移動パケットを編成単位で揃えて一括適用する。
     */
    private void flushMovementPackets(World world) {
        if (QUEUE.isEmpty() && this.pendingFormations.isEmpty()) {
            return;
        }

        this.latestByEntity.clear();
        MovementSnapshot snapshot;
        while ((snapshot = QUEUE.poll()) != null) {
            this.latestByEntity.put(snapshot.entityId, snapshot);
        }

        // 1. 新着パケットを単行/非編成（即時適用）と複数両編成（バリア同期）に振り分ける
        for (MovementSnapshot s : this.latestByEntity.values()) {
            Entity entity = world.getEntityByID(s.entityId);
            if (entity == null) {
                continue;
            }
            if (entity instanceof EntityTrainBase) {
                Formation formation = ((EntityTrainBase) entity).getFormation();
                if (formation != null && formation.entries != null && formation.entries.length > 1) {
                    PendingFormationBatch batch = this.pendingFormations.get(formation.id);
                    if (batch == null) {
                        batch = new PendingFormationBatch(this.clientTickCounter);
                        this.pendingFormations.put(formation.id, batch);
                    }
                    batch.snapshots.put(s.entityId, s);
                    continue;
                }
            }
            applySnapshot(entity, s);
        }
        this.latestByEntity.clear();

        // 2. 編成バリアの評価：ロード済み全車両が揃った編成、または待機上限に達した編成を一斉適用
        if (!this.pendingFormations.isEmpty()) {
            Iterator<Map.Entry<Long, PendingFormationBatch>> it = this.pendingFormations.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, PendingFormationBatch> entry = it.next();
                PendingFormationBatch batch = entry.getValue();
                boolean timedOut = (this.clientTickCounter - batch.firstSeenTick) >= MAX_BARRIER_WAIT_TICKS;
                boolean ready = timedOut || isFormationBatchComplete(world, batch);

                if (ready) {
                    for (MovementSnapshot s : batch.snapshots.values()) {
                        Entity entity = world.getEntityByID(s.entityId);
                        if (entity != null) {
                            applySnapshot(entity, s);
                        }
                    }
                    it.remove();
                }
            }
        }
    }

    /**
     * バッチ内の最初の有効車両が属する {@link Formation} について、
     * クライアントワールド上にロード済みの全車両のパケットが揃っているかを判定する。
     */
    private static boolean isFormationBatchComplete(World world, PendingFormationBatch batch) {
        Formation formation = null;
        for (MovementSnapshot s : batch.snapshots.values()) {
            Entity e = world.getEntityByID(s.entityId);
            if (e instanceof EntityTrainBase && !e.isDead) {
                formation = ((EntityTrainBase) e).getFormation();
                if (formation != null) {
                    break;
                }
            }
        }
        if (formation == null || formation.entries == null) {
            return true;
        }
        for (int i = 0; i < formation.entries.length; i++) {
            FormationEntry fe = formation.entries[i];
            if (fe == null || fe.train == null || fe.train.isDead || fe.train.worldObj != world) {
                continue;
            }
            if (!batch.snapshots.containsKey(fe.train.getEntityId())) {
                return false;
            }
        }
        return true;
    }

    private static void applySnapshot(Entity entity, MovementSnapshot s) {
        entity.serverPosX = s.rawX;
        entity.serverPosY = s.rawY;
        entity.serverPosZ = s.rawZ;
        double x = (double) s.rawX * DIV_32;
        double y = (double) s.rawY * DIV_32;
        double z = (double) s.rawZ * DIV_32;

        entity.setPositionAndRotation2(x, y, z, s.yaw, s.pitch, RTMEntity.FREQ_VEHICLE);
        if (entity instanceof EntityVehicleBase) {
            ((EntityVehicleBase<?>) entity).setRollAndSpeed(s.speed, s.roll);
        } else if (entity instanceof EntityBogie) {
            ((EntityBogie) entity).setRoll(s.roll);
        }
    }

    /**
     * クライアント Tick の終了時（全車両の {@code updatePosAndRotationClient()} 完了後）に、
     * 同一編成内の連結車両同士の車間距離を検証・補正し、めり込み・分離の双方を解消する。
     */
    private void solveFormationSpacing(Minecraft mc, World world) {
        List<?> entities = world.loadedEntityList;
        if (entities == null || entities.isEmpty()) {
            return;
        }

        EntityTrainBase riddenTrain = resolveRiddenTrain(mc);
        this.processedFormations.clear();

        for (int i = 0, size = entities.size(); i < size; i++) {
            Object obj = entities.get(i);
            if (!(obj instanceof EntityTrainBase)) {
                continue;
            }
            EntityTrainBase train = (EntityTrainBase) obj;
            if (train.isDead) {
                continue;
            }
            Formation formation = train.getFormation();
            if (formation == null || formation.entries == null || formation.entries.length <= 1) {
                continue;
            }
            if (!this.processedFormations.add(formation.id)) {
                continue;
            }
            this.alignFormation(world, formation, riddenTrain);
        }
    }

    /**
     * プレイヤーが現在乗車している車両（直接乗車または {@link EntityVehiclePart} 経由）を返す。
     */
    private static EntityTrainBase resolveRiddenTrain(Minecraft mc) {
        if (mc.thePlayer == null) {
            return null;
        }
        Entity riding = mc.thePlayer.ridingEntity;
        if (riding instanceof EntityTrainBase) {
            return (EntityTrainBase) riding;
        }
        if (riding instanceof EntityVehiclePart) {
            EntityVehicleBase<?> parent = ((EntityVehiclePart) riding).getVehicle();
            if (parent instanceof EntityTrainBase) {
                return (EntityTrainBase) parent;
            }
        }
        return null;
    }

    /**
     * 編成内のロード済み連続区間（セグメント）ごとに車間距離を揃える。
     */
    private void alignFormation(World world, Formation formation, EntityTrainBase riddenTrain) {
        FormationEntry[] entries = formation.entries;
        this.segmentBuffer.clear();

        for (int i = 0; i <= entries.length; i++) {
            EntityTrainBase car = null;
            if (i < entries.length) {
                FormationEntry fe = entries[i];
                if (fe != null && fe.train != null && !fe.train.isDead && fe.train.worldObj == world) {
                    car = fe.train;
                }
            }

            if (car != null) {
                this.segmentBuffer.add(car);
            } else {
                if (this.segmentBuffer.size() >= 2) {
                    alignSegment(this.segmentBuffer, riddenTrain);
                }
                this.segmentBuffer.clear();
            }
        }
    }

    /**
     * 連続する連結車両リストに対し、アンカー車両を基準として前後へ車間距離を補正する。
     */
    private static void alignSegment(List<EntityTrainBase> cars, EntityTrainBase riddenTrain) {
        int count = cars.size();
        int anchorIndex = 0;
        if (riddenTrain != null) {
            for (int i = 0; i < count; i++) {
                if (cars.get(i) == riddenTrain) {
                    anchorIndex = i;
                    break;
                }
            }
        }

        // アンカーから後方へ伝播
        for (int i = anchorIndex + 1; i < count; i++) {
            alignPair(cars.get(i - 1), cars.get(i));
        }

        // アンカーから前方へ伝播
        for (int i = anchorIndex - 1; i >= 0; i--) {
            alignPair(cars.get(i + 1), cars.get(i));
        }
    }

    /**
     * 基準車両 {@code base} に対して隣接車両 {@code target} の位置を理想連結距離へ補正する。
     * めり込み（距離不足）と分離（距離過多）の双方に対応する。
     */
    private static void alignPair(EntityTrainBase base, EntityTrainBase target) {
        double idealDist = target.getDefaultDistanceToConnectedTrain(base);
        if (Double.isNaN(idealDist) || idealDist <= 0.01D) {
            return;
        }

        double expectedDist = computeCurvedExpectedDistance(base, target, idealDist);

        double dx = target.posX - base.posX;
        double dy = target.posY - base.posY;
        double dz = target.posZ - base.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq <= 1.0E-8D) {
            return;
        }

        double actualDist = Math.sqrt(distSq);
        double error = actualDist - expectedDist;
        double absError = Math.abs(error);
        if (absError <= SPACING_DEADBAND || absError > MAX_CORRECTABLE_ERROR) {
            return;
        }

        double scale = expectedDist / actualDist;
        double newX = base.posX + dx * scale;
        double newY = base.posY + dy * scale;
        double newZ = base.posZ + dz * scale;

        double shiftX = newX - target.posX;
        double shiftY = newY - target.posY;
        double shiftZ = newZ - target.posZ;

        target.setPosition(newX, newY, newZ);

        // 台車も車体と同量シフトし、車体と台車の位置整合を維持する
        shiftBogie(target.getBogie(0), shiftX, shiftY, shiftZ);
        shiftBogie(target.getBogie(1), shiftX, shiftY, shiftZ);
    }

    /**
     * 2車両の Yaw 差（180度反転連結を考慮した折れ角）から、カーブ上の理想弦長を算出する。
     * {@code d_expected = d_ideal * sin(theta / 2) / (theta / 2)}
     */
    private static double computeCurvedExpectedDistance(EntityTrainBase base, EntityTrainBase target, double idealDist) {
        double yawDiffDeg = Math.abs(MathHelper.wrapAngleTo180_float(target.rotationYaw - base.rotationYaw));
        if (yawDiffDeg > 90.0D) {
            yawDiffDeg = 180.0D - yawDiffDeg;
        }
        if (yawDiffDeg <= 0.5D) {
            return idealDist;
        }
        if (yawDiffDeg > MAX_CURVE_ANGLE_DEG) {
            yawDiffDeg = MAX_CURVE_ANGLE_DEG;
        }
        double halfRad = Math.toRadians(yawDiffDeg * 0.5D);
        return idealDist * (Math.sin(halfRad) / halfRad);
    }

    private static void shiftBogie(EntityBogie bogie, double shiftX, double shiftY, double shiftZ) {
        if (bogie != null && !bogie.isDead) {
            bogie.setPosition(bogie.posX + shiftX, bogie.posY + shiftY, bogie.posZ + shiftZ);
        }
    }
}
