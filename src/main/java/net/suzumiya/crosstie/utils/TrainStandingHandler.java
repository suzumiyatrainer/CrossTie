package net.suzumiya.crosstie.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class TrainStandingHandler {

    private static final Set<UUID> ridingPlayers = Collections.newSetFromMap(new WeakHashMap<>());
    private static EntityTrainBase clientStandingTrain = null;

    /**
     * サーバー側で保持する立ち乗りプレイヤーの状態情報
     */
    public static class StandingPlayerState {
        public final UUID playerUUID;
        public int trainEntityId;
        public double localL; // 前後オフセット (Z)
        public double localW; // 左右オフセット (X)
        public double localY; // 床面からの相対高さ (Y)
        public int desyncTicks;
        public int cooldownTicks;
        public long lastUpdateTick;

        public StandingPlayerState(UUID playerUUID, int trainEntityId, double localL, double localW, double localY, long tick) {
            this.playerUUID = playerUUID;
            this.trainEntityId = trainEntityId;
            this.localL = localL;
            this.localW = localW;
            this.localY = localY;
            this.desyncTicks = 0;
            this.cooldownTicks = 0;
            this.lastUpdateTick = tick;
        }
    }

    private static final Map<UUID, StandingPlayerState> serverStandingStates = new HashMap<>();

    public static EntityTrainBase getClientStandingTrain() {
        return clientStandingTrain;
    }

    public static boolean isPlayerActuallyStandingOn(EntityTrainBase train, EntityPlayer player) {
        if (train == null || train.isDead || player == null || player.worldObj != train.worldObj) {
            return false;
        }
        if (player.ridingEntity != null) {
            return false;
        }
        double halfLength = getHalfLength(train);
        double halfWidth = 1.525;
        double dx = train.posX - train.prevPosX;
        double dy = train.posY - train.prevPosY;
        double dz = train.posZ - train.prevPosZ;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return isStandingOnTrain(train, halfLength, halfWidth, player, true, speed);
    }

    public static void clearClientStandingTrain(EntityTrainBase train) {
        if (clientStandingTrain == train) {
            clientStandingTrain = null;
        }
    }

    @SuppressWarnings("unchecked")
    public static void updateStandingEntities(EntityTrainBase train) {
        double dx = train.posX - train.prevPosX;
        double dy = train.posY - train.prevPosY;
        double dz = train.posZ - train.prevPosZ;
        float dYaw = MathHelper.wrapAngleTo180_float((float) (train.rotationYaw - train.prevRotationYaw));
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double halfLength = TrainStandingHandler.getHalfLength(train);
        double halfWidth = 1.525;

        // 330km/h (約4.58 blocks/tick) 超高速域でもプレイヤーを捕捉できるように移動速度に応じた検索マージンを動的に拡大
        double speedMargin = Math.max(2.5, speed * 2.5);
        double searchExtent = halfLength + halfWidth + speedMargin;
        AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(
                train.posX - searchExtent, train.posY - 4.0 - speedMargin, train.posZ - searchExtent,
                train.posX + searchExtent, train.posY + 2.0 + 5.0 + speedMargin, train.posZ + searchExtent);

        List<Entity> nearby = train.worldObj.getEntitiesWithinAABBExcludingEntity(train, searchBox);
        boolean isRemote = train.worldObj.isRemote;

        for (Entity entity : nearby) {
            if (!(entity instanceof EntityPlayer) || entity.ridingEntity != null) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) entity;
            boolean isLocalPlayer = false;
            if (isRemote) {
                isLocalPlayer = (player == Minecraft.getMinecraft().thePlayer);
                if (!isLocalPlayer) {
                    continue;
                }
            }

            UUID uuid = player.getUniqueID();
            boolean alreadyRiding = ridingPlayers.contains(uuid);
            boolean standing = TrainStandingHandler.isStandingOnTrain(train, halfLength, halfWidth, player, alreadyRiding, speed);

            if (standing) {
                // 視線旋回 (Yaw) の追従
                if (dYaw != 0.0f) {
                    player.rotationYaw -= dYaw;
                    if (isRemote && isLocalPlayer) {
                        player.prevRotationYaw -= dYaw;
                    }
                }

                if (isRemote && isLocalPlayer) {
                    // クライアント側（自分）: 3次元ローカル追従
                    movePlayerWithTrainClient(player, train);
                    clientStandingTrain = train;
                } else if (!isRemote && player instanceof EntityPlayerMP) {
                    // サーバー側: 状態管理・号車間移動・乖離判定・救済措置
                    updateServerStandingState((EntityPlayerMP) player, train, halfLength, speed);
                }

                ridingPlayers.add(uuid);
            } else {
                if (alreadyRiding) {
                    ridingPlayers.remove(uuid);
                    if (isRemote && isLocalPlayer && clientStandingTrain == train) {
                        clientStandingTrain = null;
                    } else if (!isRemote) {
                        serverStandingStates.remove(uuid);
                    }
                }
            }
        }

        // クライアント側: ローカルプレイヤーがこの電車の範囲外に出た場合、確実にclientStandingTrainを解除
        if (isRemote && clientStandingTrain == train) {
            EntityPlayer localPlayer = Minecraft.getMinecraft().thePlayer;
            if (localPlayer == null || !isPlayerActuallyStandingOn(train, localPlayer)) {
                clientStandingTrain = null;
                if (localPlayer != null) {
                    ridingPlayers.remove(localPlayer.getUniqueID());
                }
            }
        }

        // サーバー側のクリーンアップ（死亡・切断・別ディメンション）
        if (!isRemote && train.ticksExisted % 100 == 0) {
            cleanupServerStates(train);
        }
    }

    /**
     * サーバー側での立ち乗り状態管理と、超高速・ラグ時の救済処理
     */
    private static void updateServerStandingState(EntityPlayerMP player, EntityTrainBase train, double halfLength, double speed) {
        UUID uuid = player.getUniqueID();
        long currentTick = train.worldObj.getTotalWorldTime();
        StandingPlayerState state = serverStandingStates.get(uuid);

        if (state == null || state.trainEntityId != train.getEntityId()) {
            // 新規搭乗、または別車両からの引き継ぎ
            Vec3 local = computeLocalPos(train, player.posX, player.posY, player.posZ);
            state = new StandingPlayerState(uuid, train.getEntityId(), local.zCoord, local.xCoord, local.yCoord, currentTick);
            serverStandingStates.put(uuid, state);
            return;
        }

        // 編成内（号車間）の渡り歩き対応: 車両長を超えて歩いた場合に隣の連結車両へターゲットを自動委譲
        if (Math.abs(state.localL) > halfLength + 0.3) {
            int dir = state.localL > 0 ? 0 : 1;
            EntityTrainBase connected = train.getConnectedTrain(dir);
            if (connected != null && !connected.isDead) {
                Vec3 newLocal = computeLocalPos(connected, player.posX, player.posY, player.posZ);
                state.trainEntityId = connected.getEntityId();
                state.localL = newLocal.zCoord;
                state.localW = newLocal.xCoord;
                state.localY = newLocal.yCoord;
                return;
            }
        }

        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
        }

        // サーバー側で期待されるワールド座標（姿勢・傾き・カントを3次元反映）
        Vec3 expectedPos = computeTransformedWorldPos(train, state.localL, state.localW, state.localY);

        // プレイヤーの実際の座標と期待座標の乖離距離
        double dx = player.posX - expectedPos.xCoord;
        double dy = player.posY - expectedPos.yCoord;
        double dz = player.posZ - expectedPos.zCoord;
        double distSq = dx * dx + dy * dy + dz * dz;

        // 330km/h (4.58 blocks/tick) でも壊れない動的しきい値
        // プレイヤーの最大歩行/ダッシュ速度 (約0.6 blocks/tick) + 電車の移動速度 * 遅延許容係数 + バッファ
        double threshold = Math.max(2.5, 0.6 + speed * 1.5 + 2.0);
        double thresholdSq = threshold * threshold;

        if (distSq > thresholdSq) {
            state.desyncTicks++;
            // 2tick連続で閾値超過し、クールダウンが明けている場合にサーバー側から強制同期（救済）
            if (state.desyncTicks >= 2 && state.cooldownTicks == 0) {
                player.setPositionAndUpdate(expectedPos.xCoord, expectedPos.yCoord, expectedPos.zCoord);
                player.fallDistance = 0.0F;
                player.motionX = 0.0;
                player.motionY = 0.0;
                player.motionZ = 0.0;
                player.onGround = true;

                state.desyncTicks = 0;
                state.cooldownTicks = 6; // 0.3秒のクールダウンを設けて連続テレポートを防止
            }
        } else {
            // 正常範囲内: 通常はクライアント主従なので、クライアントから届いた座標を信頼してローカル座標を更新
            Vec3 currentLocal = computeLocalPos(train, player.posX, player.posY, player.posZ);
            state.localL = currentLocal.zCoord;
            state.localW = currentLocal.xCoord;
            state.localY = currentLocal.yCoord;
            state.desyncTicks = 0;
        }

        state.lastUpdateTick = currentTick;
    }

    /**
     * クライアント側（ローカルプレイヤー）の3次元ローカル追従
     * カント（Roll）や勾配（Pitch）による車体の立体的な傾き・変位に追従
     */
    private static void movePlayerWithTrainClient(EntityPlayer player, EntityTrainBase train) {
        // 前tickの電車の姿勢から現在のプレイヤーのローカル相対座標を逆算
        Vec3 localVec = computeLocalPosPrev(train, player.posX, player.posY, player.posZ);

        // 新しい電車の姿勢・座標にローカル座標を投影して目標ワールド座標を算出
        Vec3 targetWorld = computeTransformedWorldPos(train, localVec.zCoord, localVec.xCoord, 0.0);

        // 水平方向および垂直方向の補正移動量
        double targetX = targetWorld.xCoord;
        double targetZ = targetWorld.zCoord;
        double moveX = targetX - player.posX;
        double moveZ = targetZ - player.posZ;

        // 床面高さの計算
        double targetFloorY = computeFloorY(train, localVec.zCoord, localVec.xCoord);
        double yError = targetFloorY - player.boundingBox.minY;
        double yCorrection = 0.0;
        if (player.boundingBox.minY - targetFloorY < 0.6) {
            yCorrection = MathHelper.clamp_double(yError * 0.4, -0.6, 0.6);
            if (Math.abs(yError) > 0.6) {
                yCorrection = yError;
            }
        }

        boolean prevNoClip = player.noClip;
        player.noClip = true;
        player.moveEntity(moveX, yCorrection, moveZ);
        player.noClip = prevNoClip;

        player.motionY = 0.0;
        player.fallDistance = 0.0f;
        player.onGround = true;
    }

    private static void cleanupServerStates(EntityTrainBase train) {
        long currentTick = train.worldObj.getTotalWorldTime();
        Iterator<Map.Entry<UUID, StandingPlayerState>> it = serverStandingStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, StandingPlayerState> entry = it.next();
            if (currentTick - entry.getValue().lastUpdateTick > 60) {
                it.remove();
            }
        }
    }

    private static double getHalfLength(EntityTrainBase train) {
        TrainConfig cfg = (TrainConfig) train.getModelSet().getConfig();
        if (cfg == null) {
            return 1.375;
        }
        return cfg.trainDistance;
    }

    private static boolean isStandingOnTrain(EntityTrainBase train, double halfLength, double halfWidth, Entity entity,
            boolean alreadyRiding, double speed) {
        Vec3 localVec = computeLocalPos(train, entity.posX, entity.posY, entity.posZ);
        double localLength = localVec.zCoord;
        double localWidth = localVec.xCoord;

        // 高速域でカーブや加減速時に脱落しないよう、マージンを動的に調整
        double extraMargin = Math.min(1.5, speed * 0.3);
        double widthMargin = alreadyRiding ? (halfWidth + 0.6 + extraMargin) : halfWidth;
        double lengthMargin = alreadyRiding ? (halfLength + 0.6 + extraMargin) : halfLength;

        boolean horizontalOverlap = Math.abs(localWidth) <= widthMargin && Math.abs(localLength) <= lengthMargin;
        if (!horizontalOverlap) {
            return false;
        }

        double floorY = computeFloorY(train, localLength, localWidth);
        double feetY = entity.boundingBox.minY;
        double minVert = -2.5 - extraMargin;
        double maxVert = alreadyRiding ? (2.2 + extraMargin) : 1.8;
        double vertDiff = feetY - floorY;

        return vertDiff >= minVert && vertDiff <= maxVert;
    }

    /**
     * 電車中心座標（train.posY ≒ 67.09560）から立ち乗り床面（Y 65.35）への高さオフセット。
     * 実測値: 地面Y64、レール底面64.06250のとき、train.posY = 67.09560。
     * 本来あるべき床面高さ Y65.35 に合わせるため、オフセットは 65.35 - 67.09560 = -1.7456D。
     */
    public static final double FLOOR_HEIGHT_OFFSET = -1.7456D;

    /**
     * 電車のローカル座標系 (前後L, 左右W, 床面からの相対高さlocalY) からワールド座標へ3次元変換（Yaw, Pitch, Roll を完全適用）
     */
    public static Vec3 computeTransformedWorldPos(EntityTrainBase train, double localL, double localW, double localY) {
        Vec3 vec = Vec3.createVectorHelper(localW, localY + FLOOR_HEIGHT_OFFSET, localL);
        vec.rotateAroundZ((float) Math.toRadians(-train.rotationRoll));
        vec.rotateAroundX((float) Math.toRadians(train.rotationPitch));
        vec.rotateAroundY((float) Math.toRadians(train.rotationYaw));

        return Vec3.createVectorHelper(train.posX + vec.xCoord, train.posY + vec.yCoord, train.posZ + vec.zCoord);
    }

    /**
     * ワールド座標から電車の現在のローカル座標系 (前後L, 左右W, 床面からの相対高さlocalY) への逆変換
     */
    public static Vec3 computeLocalPos(EntityTrainBase train, double worldX, double worldY, double worldZ) {
        double relX = worldX - train.posX;
        double relY = worldY - train.posY;
        double relZ = worldZ - train.posZ;

        Vec3 vec = Vec3.createVectorHelper(relX, relY, relZ);
        vec.rotateAroundY((float) Math.toRadians(-train.rotationYaw));
        vec.rotateAroundX((float) Math.toRadians(-train.rotationPitch));
        vec.rotateAroundZ((float) Math.toRadians(train.rotationRoll));

        return Vec3.createVectorHelper(vec.xCoord, vec.yCoord - FLOOR_HEIGHT_OFFSET, vec.zCoord);
    }

    /**
     * ワールド座標から電車の「前tickの姿勢」のローカル座標系への逆変換（クライアント追従用）
     */
    private static Vec3 computeLocalPosPrev(EntityTrainBase train, double worldX, double worldY, double worldZ) {
        double relX = worldX - train.prevPosX;
        double relY = worldY - train.prevPosY;
        double relZ = worldZ - train.prevPosZ;

        Vec3 vec = Vec3.createVectorHelper(relX, relY, relZ);
        vec.rotateAroundY((float) Math.toRadians(-train.prevRotationYaw));
        vec.rotateAroundX((float) Math.toRadians(-train.prevRotationPitch));
        vec.rotateAroundZ((float) Math.toRadians(train.prevRotationRoll));

        return Vec3.createVectorHelper(vec.xCoord, vec.yCoord - FLOOR_HEIGHT_OFFSET, vec.zCoord);
    }

    public static Vec3 computeFloorWorldPos(EntityTrainBase train, double localL, double localW) {
        return computeTransformedWorldPos(train, localL, localW, 0.0);
    }

    public static double computeFloorY(EntityTrainBase train, double localL, double localW) {
        return computeTransformedWorldPos(train, localL, localW, 0.0).yCoord;
    }
}
