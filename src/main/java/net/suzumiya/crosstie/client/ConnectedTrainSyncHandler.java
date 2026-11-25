package net.suzumiya.crosstie.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.RTMEntity;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.suzumiya.crosstie.CrossTieConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * [実験的] 連結車両走行時のめり込み防止のため、Netty IO スレッドで受信した
 * {@code PacketVehicleMovement} をクライアントメインスレッドの Tick 開始時
 * ({@link TickEvent.Phase#START}) に一括適用する同期ハンドラ。
 *
 * <p>
 * RTM / KaizPatchX の標準実装では {@code PacketVehicleMovement#onMessage} が
 * Netty IO スレッドから直接 {@code Entity#setPositionAndRotation2} を呼び出すため、
 * {@code WorldClient#updateEntities()} の途中で編成の一部車両だけ目標座標と補間カウンタ
 * ({@code vehiclePosRotationInc}) が書き換わり、前後車両間に 1〜2 tick 分の位置ズレ
 * （高速時ほど大きなめり込み）が発生する。
 * </p>
 *
 * <p>
 * {@link CrossTieConfig#fixConnectedTrainClipping} が {@code true} の場合のみ有効。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ConnectedTrainSyncHandler {

    private static final double DIV_32 = 1.0D / 32.0D;
    private static final int MAX_QUEUE_SIZE = 8192;

    private static final ConcurrentLinkedQueue<MovementSnapshot> QUEUE = new ConcurrentLinkedQueue<>();

    /** 同一 tick 内で同一エンティティの最新パケットのみを残すための再利用マップ（クライアントメインスレッド専用） */
    private final Map<Integer, MovementSnapshot> latestByEntity = new LinkedHashMap<>();

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

    /**
     * クライアント Tick の先頭（{@code WorldClient#updateEntities()} 実行前）に、
     * 蓄積された全車両の移動パケットを一括適用し、同一編成内の補間開始タイミングを完全に揃える。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        if (!CrossTieConfig.fixConnectedTrainClipping) {
            if (!QUEUE.isEmpty()) {
                QUEUE.clear();
            }
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = (mc != null) ? mc.theWorld : null;
        if (world == null) {
            if (!QUEUE.isEmpty()) {
                QUEUE.clear();
            }
            return;
        }

        if (QUEUE.isEmpty()) {
            return;
        }

        this.latestByEntity.clear();
        MovementSnapshot snapshot;
        while ((snapshot = QUEUE.poll()) != null) {
            this.latestByEntity.put(snapshot.entityId, snapshot);
        }

        if (this.latestByEntity.isEmpty()) {
            return;
        }

        for (MovementSnapshot s : this.latestByEntity.values()) {
            Entity entity = world.getEntityByID(s.entityId);
            if (entity == null) {
                continue;
            }
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

        this.latestByEntity.clear();
    }
}
