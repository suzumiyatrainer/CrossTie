package net.suzumiya.crosstie.utils.js;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.ngt.ngtlib.io.NGTLog;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.train.util.TrainState.TrainStateType;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class CrossTieConductorUtils {
    // --- デバッグログ設定 (trueで一番近いプレイヤーのチャットにデバッグログを出力) ---
    public static final boolean DEBUG = false;
    public static final int DEBUG_INTERVAL_TICKS = 600; // 定期ログ出力間隔 (600tick = 30秒)

    // 車両ごとのDataMapスナップショット保持用 (変更検知用)
    private static final Map<Integer, Map<String, Object>> lastDataMapSnapshots = new HashMap<>();

    private static final String[] TRACKED_BOOL_KEYS = { "suzu_cabIsLeft", "suzu_cabIsRight", "suzu_isMelodyToggle",
            "suzu_isBuzzer", "suzu_isBell", "suzu_isEB", "suzu_isMelody", "suzu_fdToggleReq", "SZ_isFD",
            "suzu_isBuzzerFormation", "suzu_isBellFormation", "suzu_isMelodyFormation", "suzu_keyPlayerIsLeft",
            "suzu_keys_BuzzerKey", "suzu_keys_BellKey", "suzu_keys_DoorControlKey", "suzu_keys_NextAnnounceKey",
            "suzu_keys_PrevAnnounceKey", "suzu_keys_AnnounceKey", "suzu_keys_EBKey", "suzu_keys_MelodyKey",
            "suzu_keys_TojimeKey", "suzu_keys_FDCtrlKey" };

    private static final String[] TRACKED_STR_KEYS = { "suzu_cabPlayers" };

    /**
     * 車両から一番近いプレイヤーを取得
     */
    private static EntityPlayer getNearestPlayer(World world, EntityTrainBase train) {
        if (world == null || world.playerEntities == null || world.playerEntities.isEmpty()) {
            return null;
        }
        EntityPlayer nearest = null;
        double minDistanceSq = Double.MAX_VALUE;
        for (Object obj : world.playerEntities) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) obj;
                double dSq = p.getDistanceSqToEntity(train);
                if (dSq < minDistanceSq) {
                    minDistanceSq = dSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    /**
     * 電車から一番近いプレイヤーにデバッグチャットメッセージを送信
     */
    @SuppressWarnings("unused")
    private static void sendDebugChat(EntityTrainBase train, String message) {
        if (!DEBUG || train == null || train.worldObj == null) {
            return;
        }
        @SuppressWarnings("unused")
        EntityPlayer nearest = getNearestPlayer(train.worldObj, train);
        if (nearest != null) {
            try {
                NGTLog.sendChatMessage(nearest, "\u00a7e[CrossTie Debug]\u00a7r " + message);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * DataMapの値変化を監視し、いずれかの値が切り替わったときのみログ出力
     */
    @SuppressWarnings("unused")
    private static void checkAndLogDataMapChanges(EntityTrainBase train, Object dataMapObj, Method getBoolean,
            Method getString) {
        if (!DEBUG || train == null || dataMapObj == null)
            return;
        @SuppressWarnings("unused")
        int trainId = train.getEntityId();
        Map<String, Object> lastSnapshot = lastDataMapSnapshots.get(trainId);
        boolean isFirst = (lastSnapshot == null);
        if (isFirst) {
            lastSnapshot = new HashMap<>();
            lastDataMapSnapshots.put(trainId, lastSnapshot);
        }

        Map<String, Object> currentSnapshot = new HashMap<>();
        List<String> changedEntries = new ArrayList<>();

        for (String key : TRACKED_BOOL_KEYS) {
            try {
                boolean val = (Boolean) getBoolean.invoke(dataMapObj, key);
                currentSnapshot.put(key, val);
                if (!isFirst) {
                    @SuppressWarnings("null")
                    Object oldVal = lastSnapshot.get(key);
                    if (oldVal == null || !oldVal.equals(val)) {
                        changedEntries.add(key + ": " + oldVal + " -> " + val);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (String key : TRACKED_STR_KEYS) {
            try {
                String val = (String) getString.invoke(dataMapObj, key);
                val = (val == null) ? "" : val;
                currentSnapshot.put(key, val);
                if (!isFirst) {
                    @SuppressWarnings("null")
                    Object oldVal = lastSnapshot.get(key);
                    if (oldVal == null || !oldVal.equals(val)) {
                        changedEntries.add(key + ": \"" + oldVal + "\" -> \"" + val + "\"");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        lastDataMapSnapshots.put(trainId, currentSnapshot);

        if (!changedEntries.isEmpty()) {
            String msg = String.format("[Train #%d] \u00a7bDataMap変更\u00a7r: %s", trainId,
                    String.join(", ", changedEntries));
            sendDebugChat(train, msg);
        }
    }

    // Throttle Lib (setThrottledBoolean/Int/String) は CrossTieDMUtils に移管済み。

    private static int charToIndex(char ch) {
        ch = Character.toUpperCase(ch);
        if (ch == '-')
            return -1;
        if (ch >= '0' && ch <= '9')
            return ch - '0';
        if (ch >= 'A' && ch <= 'Z')
            return ch - 'A' + 10;
        return 0;
    }

    // --- Server ---
    public static void processConductorServer(Object entityObj, Object dataMapObj, boolean isDoubleCab, double minZ,
            double maxZ, double posX, String syncKeysCsv, String syncKeysBoolCsv) {
        if (!(entityObj instanceof EntityTrainBase))
            return;
        EntityTrainBase train = (EntityTrainBase) entityObj;
        World world = train.worldObj;
        if (world == null)
            return;

        try {
            Method getInt = dataMapObj.getClass().getMethod("getInt", String.class);
            Method setInt = dataMapObj.getClass().getMethod("setInt", String.class, int.class, int.class);
            Method getBoolean = dataMapObj.getClass().getMethod("getBoolean", String.class);
            Method setBoolean = dataMapObj.getClass().getMethod("setBoolean", String.class, boolean.class, int.class);
            Method getString = dataMapObj.getClass().getMethod("getString", String.class);
            Method setString = dataMapObj.getClass().getMethod("setString", String.class, String.class, int.class);

            int trainTick = train.ticksExisted;

            @SuppressWarnings("unchecked")
            List<EntityPlayer> players = world.playerEntities;

            List<String> playerInCab = new ArrayList<>();
            boolean cabIsLeftDetected = false;
            boolean cabIsRightDetected = false;
            float yaw = -train.rotationYaw;

            for (EntityPlayer target : players) {
                double dx = target.posX - train.posX;
                double dy = target.posY - train.posY;
                double dz = target.posZ - train.posZ;

                Vec3 targetVec = Vec3.createVectorHelper(dx, dy, dz);
                targetVec.rotateAroundY((float) Math.toRadians(yaw));
                double x = targetVec.xCoord;
                double y = targetVec.yCoord;
                double z = targetVec.zCoord;

                // 純JS・カスタムボタン判定と完全一致させるため、Entity ID (数値文字列) を使用
                String targetIDStr = String.valueOf(target.getEntityId());

                boolean inBounds = ((-posX < x && x < posX) && (minZ < z && z < maxZ) && (-0.5 < y && y < 2.0));
                boolean inDoubleBounds = (isDoubleCab && (-posX < x && x < posX) && (-minZ > z && z > -maxZ)
                        && (-0.5 < y && y < 2.0));

                if (inBounds || inDoubleBounds) {
                    playerInCab.add(targetIDStr);
                    if (x < 0) {
                        cabIsLeftDetected = true;
                    } else {
                        cabIsRightDetected = true;
                    }
                }

            }

            // --- 無条件で運転席のプレイヤーはキャブ内にいるとみなす ---
            Object riddenByEntity = train.riddenByEntity;
            if (riddenByEntity != null && riddenByEntity instanceof EntityPlayer) {
                String driverID = String.valueOf(((EntityPlayer) riddenByEntity).getEntityId());
                if (!playerInCab.contains(driverID)) {
                    playerInCab.add(driverID);
                }
                if (!cabIsLeftDetected && !cabIsRightDetected) {
                    cabIsLeftDetected = true;
                }
            }

            // 古いプレイヤーリストを取得して、退出したプレイヤーのフラグを解除
            String prevPlayersStr = (String) getString.invoke(dataMapObj, "suzu_cabPlayers");
            if (prevPlayersStr != null && !prevPlayersStr.isEmpty()) {
                for (String p : prevPlayersStr.split(",")) {
                    if (!playerInCab.contains(p)) {
                        CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_cab_" + p, false,
                                3);
                    }
                }
            }

            // 現在のプレイヤーのフラグをセット
            for (String p : playerInCab) {
                CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_cab_" + p, true, 3);
            }

            String newCabPlayers = String.join(",", playerInCab);
            if (trainTick % 20 == 0) {
                // マルチプレイで追跡開始時のパケット欠損/遅延対策として、1秒ごとに強制同期
                setString.invoke(dataMapObj, "suzu_cabPlayers", newCabPlayers, 3);
            } else {
                CrossTieDMUtils.setThrottledString(dataMapObj, getString, setString, "suzu_cabPlayers", newCabPlayers,
                        3);
            }
            CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_cabIsLeft", cabIsLeftDetected,
                    3);
            CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_cabIsRight",
                    cabIsRightDetected, 3);

            // 運番(編成番号)同期: 純JS準拠 (10tick毎)
            if (trainTick % 10 == 0) {
                try {
                    String trainName = train.getResourceState().getName();
                    if (trainName != null && !trainName.isEmpty() && !"no_name".equals(trainName)
                            && trainName.length() >= 5) {
                        for (int csi = 0; csi < 5; csi++) {
                            int csVal = charToIndex(trainName.charAt(csi));
                            CrossTieDMUtils.setThrottledInt(dataMapObj, getInt, setInt, "SZ_CS" + csi, csVal, 3);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            String playerListStr = (String) getString.invoke(dataMapObj, "suzu_cabPlayers");
            List<String> playerIDs = new ArrayList<>();
            if (playerListStr != null && !playerListStr.isEmpty()) {
                for (String p : playerListStr.split(",")) {
                    playerIDs.add(p);
                }
            }

            if (playerIDs.isEmpty() && (Boolean) getBoolean.invoke(dataMapObj, "suzu_isMelodyToggle")) {
                CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_isMelodyToggle", false,
                        3);
            }

            boolean anyInCab = !playerIDs.isEmpty();
            boolean cabIsLeft = (Boolean) getBoolean.invoke(dataMapObj, "suzu_cabIsLeft");

            boolean pushBuzzer = anyInCab && checkInputKey(dataMapObj, getBoolean, setBoolean, "BuzzerKey", false);
            boolean pushBell = anyInCab && checkInputKey(dataMapObj, getBoolean, setBoolean, "BellKey", true);
            boolean pushMelody = false;
            boolean pushNextAnnounce = false;
            boolean pushPrevAnnounce = false;
            boolean pushPlayAnnounce = false;
            boolean pushTojimeAnnounce = anyInCab
                    && checkInputKey(dataMapObj, getBoolean, setBoolean, "TojimeKey", true);
            boolean pushEB = false;
            boolean pushDoorSwitch = false;
            boolean pushFDCtrl = anyInCab && checkInputKey(dataMapObj, getBoolean, setBoolean, "FDCtrlKey", true);

            if (anyInCab && checkInputKey(dataMapObj, getBoolean, setBoolean, "MelodyKey", true)) {
                boolean curMelody = (Boolean) getBoolean.invoke(dataMapObj, "suzu_isMelodyToggle");
                CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_isMelodyToggle",
                        !curMelody, 3);
            }
            if ((Boolean) getBoolean.invoke(dataMapObj, "suzu_isMelodyToggle")) {
                pushMelody = true;
            }

            if (anyInCab) {
                pushNextAnnounce = checkInputKey(dataMapObj, getBoolean, setBoolean, "NextAnnounceKey", true);
                pushPrevAnnounce = checkInputKey(dataMapObj, getBoolean, setBoolean, "PrevAnnounceKey", true);
                pushPlayAnnounce = checkInputKey(dataMapObj, getBoolean, setBoolean, "AnnounceKey", true);
                pushEB = checkInputKey(dataMapObj, getBoolean, setBoolean, "EBKey", true);
                pushDoorSwitch = checkInputKey(dataMapObj, getBoolean, setBoolean, "DoorControlKey", true);
            }

            // --- RTM 標準運転席とのキー競合防止 (撤廃: 運転士でも操作可能にする) ---
            Entity rider = train.riddenByEntity;
            if (rider instanceof EntityPlayer) {
                String riderID = String.valueOf(((EntityPlayer) rider).getEntityId());
                if (playerIDs.contains(riderID) && playerIDs.size() == 1) {
                    // pushDoorSwitch = false; // 運転席でも操作可能にするためコメントアウト
                    // pushNextAnnounce = false;
                    // pushPrevAnnounce = false;
                }
            }

            // ドア制御・アナウンス制御をここで直接実行(JS側への処理委譲は廃止)
            processDoorAndAnnounce(train, world, pushNextAnnounce, pushPrevAnnounce, pushPlayAnnounce,
                    pushTojimeAnnounce, pushDoorSwitch, cabIsLeft, playerIDs);

            CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_isBuzzer", pushBuzzer, 1);
            CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_isBell", pushBell, 1);
            CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_isEB", pushEB, 1);
            CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_isMelody", pushMelody, 3);

            if (pushFDCtrl) {
                CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "suzu_fdToggleReq", true, 3);
            }

            // --- デバッグログ (車掌判定) ---
            if (DEBUG) {
                boolean cabChanged = (prevPlayersStr == null && !playerInCab.isEmpty())
                        || (prevPlayersStr != null && !prevPlayersStr.equals(String.join(",", playerInCab)));
                if (cabChanged || (trainTick % DEBUG_INTERVAL_TICKS == 0)) {
                    EntityPlayer nearest = getNearestPlayer(world, train);
                    if (nearest != null) {
                        String nearestID = String.valueOf(nearest.getEntityId());
                        boolean inCab = playerInCab.contains(nearestID);
                        boolean isDriver = (train.riddenByEntity == nearest);
                        boolean isConductor = inCab && !isDriver;

                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("[Train #%d] %s: 車掌判定=", train.getEntityId(),
                                nearest.getCommandSenderName()));
                        if (isConductor) {
                            sb.append("\u00a7a[TRUE]\u00a7r");
                        } else if (inCab && isDriver) {
                            sb.append("\u00a7e[運転士(キャブ内)]\u00a7r");
                        } else {
                            sb.append("\u00a7c[FALSE]\u00a7r");
                        }
                        sb.append(String.format(" (キャブ内:%s, 運転席:%s, 乗務員室人数:%d)", inCab ? "\u00a7atrue\u00a7r" : "false",
                                isDriver ? "\u00a7atrue\u00a7r" : "false", playerInCab.size()));
                        if (cabChanged) {
                            sb.append(" \u00a7b[状態変化]\u00a7r");
                        }
                        sendDebugChat(train, sb.toString());
                    }
                }
            }

            // 編成同期
            String[] syncKeys = (syncKeysCsv != null && !syncKeysCsv.isEmpty()) ? syncKeysCsv.split(",")
                    : new String[0];
            String[] syncKeysBool = (syncKeysBoolCsv != null && !syncKeysBoolCsv.isEmpty()) ? syncKeysBoolCsv.split(",")
                    : new String[0];
            syncFormationServer(train, dataMapObj, getInt, setInt, getBoolean, setBoolean, getString, setString,
                    trainTick, syncKeys, syncKeysBool);

            // --- デバッグログ (DataMap変更検知: いずれかのDataMap値が切り替わったときのみ送信) ---
            if (DEBUG) {
                checkAndLogDataMapChanges(train, dataMapObj, getBoolean, getString);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 編成間のDataMap同期を統括する。 汎用的なキー同期処理そのものは CrossTieDMUtils に委譲し、
     * ここでは「ブザー/電鈴/メロディの編成内OR集約」「EBノッチ反映」「FDトグル」といった このシステム固有のドメインロジックのみを扱う。
     */
    private static void syncFormationServer(EntityTrainBase train, Object dataMapObj, Method getInt, Method setInt,
            Method getBoolean, Method setBoolean, Method getString, Method setString, int trainTick, String[] syncKeys,
            String[] syncKeysBool) throws Exception {
        Formation formation = train.getFormation();
        if (formation == null) {
            formation = jp.ngt.rtm.entity.train.util.FormationManager.getInstance().createNewFormation(train);
            train.setFormation(formation);
        }
        if (formation == null)
            return;

        if (!train.isControlCar())
            return;

        boolean anyFDReq = false;
        boolean isBuzzerFormation = false;
        boolean isBellFormation = false;
        boolean isMelodyFormation = false;

        for (int i = 0; i < formation.size(); i++) {
            EntityTrainBase fEntity = CrossTieDMUtils.getTrainFromFormationEntry(formation.get(i));
            if (fEntity == null)
                continue;
            Object fData = CrossTieDMUtils.getFormationDataMap(fEntity);
            if (fData == null)
                continue;

            if (!anyFDReq && (Boolean) getBoolean.invoke(fData, "suzu_fdToggleReq")) {
                anyFDReq = true;
            }
            if ((Boolean) getBoolean.invoke(fData, "suzu_isBuzzer"))
                isBuzzerFormation = true;
            if ((Boolean) getBoolean.invoke(fData, "suzu_isBell"))
                isBellFormation = true;
            if ((Boolean) getBoolean.invoke(fData, "suzu_isMelody"))
                isMelodyFormation = true;
            if ((Boolean) getBoolean.invoke(fData, "suzu_isEB")) {
                fEntity.setNotch(-8);
            }
        }

        // 全車へ特殊フラグ(ブザー/電鈴/メロディの編成内集約結果)を反映
        CrossTieDMUtils.broadcastBooleanToFormation(formation, getBoolean, setBoolean, "suzu_isBuzzerFormation",
                isBuzzerFormation);
        CrossTieDMUtils.broadcastBooleanToFormation(formation, getBoolean, setBoolean, "suzu_isBellFormation",
                isBellFormation);
        CrossTieDMUtils.broadcastBooleanToFormation(formation, getBoolean, setBoolean, "suzu_isMelodyFormation",
                isMelodyFormation);

        // カスボ・運番等同期: 呼び出し側(JS/Java)から渡されたキー名リストを対象に汎用同期を行う
        CrossTieDMUtils.syncFormationKeys(train, dataMapObj, getInt, setInt, getBoolean, setBoolean, getString,
                setString, trainTick, syncKeys, syncKeysBool, null);

        // ホームドア(FD)トグル制御
        int fdTimer = (Integer) getInt.invoke(dataMapObj, "suzu_fdTimer");
        if (fdTimer > 0) {
            setInt.invoke(dataMapObj, "suzu_fdTimer", fdTimer - 1, 0);
        }

        if (anyFDReq) {
            if (fdTimer == 0) {
                boolean newFD = !(Boolean) getBoolean.invoke(dataMapObj, "SZ_isFD");
                for (int i = 0; i < formation.size(); i++) {
                    EntityTrainBase fEntity = CrossTieDMUtils.getTrainFromFormationEntry(formation.get(i));
                    if (fEntity == null)
                        continue;
                    Object targetDM = CrossTieDMUtils.getFormationDataMap(fEntity);
                    if (targetDM == null)
                        continue;
                    CrossTieDMUtils.setThrottledBoolean(targetDM, getBoolean, setBoolean, "suzu_fdToggleReq", false, 3);
                    CrossTieDMUtils.setThrottledBoolean(targetDM, getBoolean, setBoolean, "SZ_isFD", newFD, 3);
                }
                CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "last_sync_SZ_isFD", newFD, 3);
                setInt.invoke(dataMapObj, "suzu_fdTimer", 5, 0);
            } else {
                for (int i = 0; i < formation.size(); i++) {
                    EntityTrainBase fEntity = CrossTieDMUtils.getTrainFromFormationEntry(formation.get(i));
                    if (fEntity == null)
                        continue;
                    Object targetDM = CrossTieDMUtils.getFormationDataMap(fEntity);
                    if (targetDM == null)
                        continue;
                    CrossTieDMUtils.setThrottledBoolean(targetDM, getBoolean, setBoolean, "suzu_fdToggleReq", false, 3);
                }
            }
        } else if (trainTick % 10 == 0) {
            boolean controlFD = (Boolean) getBoolean.invoke(dataMapObj, "SZ_isFD");
            boolean lastFD = (Boolean) getBoolean.invoke(dataMapObj, "last_sync_SZ_isFD");
            if (controlFD != lastFD || trainTick % 100 == 0) {
                for (int i = 0; i < formation.size(); i++) {
                    EntityTrainBase fEntity = CrossTieDMUtils.getTrainFromFormationEntry(formation.get(i));
                    if (fEntity == null)
                        continue;
                    Object targetDM = CrossTieDMUtils.getFormationDataMap(fEntity);
                    if (targetDM == null)
                        continue;
                    CrossTieDMUtils.setThrottledBoolean(targetDM, getBoolean, setBoolean, "SZ_isFD", controlFD, 3);
                }
                CrossTieDMUtils.setThrottledBoolean(dataMapObj, getBoolean, setBoolean, "last_sync_SZ_isFD", controlFD,
                        3);
            }
        }
    }

    /**
     * ドア開閉・アナウンス切替・詰め案内を実際に実行する。 (旧JS実装のアクション実行部をそのまま移植したもの)
     */
    private static void processDoorAndAnnounce(EntityTrainBase train, World world, boolean pushNextAnnounce,
            boolean pushPrevAnnounce, boolean pushPlayAnnounce, boolean pushTojimeAnnounce, boolean pushDoorSwitch,
            boolean cabIsLeft, List<String> playerIDs) {
        try {
            // 操作者(changer)特定: 車掌役プレイヤー(playerIDs先頭)をworldから解決
            EntityPlayer changer = null;
            if ((pushNextAnnounce || pushPrevAnnounce || pushPlayAnnounce) && !playerIDs.isEmpty()) {
                String changerIDStr = playerIDs.get(0);
                @SuppressWarnings("unchecked")
                List<EntityPlayer> players = world.playerEntities;
                for (EntityPlayer p : players) {
                    if (String.valueOf(p.getEntityId()).equals(changerIDStr)) {
                        changer = p;
                        break;
                    }
                }
            }

            // --- アナウンス制御 ---
            TrainConfig config = train.getModelSet().getConfig();
            String[][] announceList = config.sound_Announcement;
            if (announceList != null && announceList.length > 0) {
                int announceMax = announceList.length - 1;
                Entity rider = train.riddenByEntity;
                // 運転士(rider)操作時はRTM標準機能側が処理するため、車掌(非rider)操作時のみ処理し二重発動を防止
                boolean isRider = (changer != null && rider != null && changer.equals(rider));

                if (!isRider && changer != null) {
                    int announceIndex = train.getTrainStateData(TrainStateType.State_Announcement.id);
                    if (pushNextAnnounce)
                        announceIndex++;
                    if (pushPrevAnnounce)
                        announceIndex--;
                    announceIndex = announceIndex < 0 ? announceMax : (announceIndex > announceMax ? 0 : announceIndex);

                    if (pushNextAnnounce || pushPrevAnnounce) {
                        try {
                            NGTLog.sendChatMessage(changer, "Current announce:" + announceList[announceIndex][0]);
                        } catch (Exception ignored) {
                        }
                        train.setTrainStateData(TrainStateType.State_Announcement.id, (byte) announceIndex);
                    }
                    if (pushPlayAnnounce) {
                        playSoundToFormation(train, announceList[announceIndex][1]);
                    }
                }
            }
            if (pushTojimeAnnounce) {
                playSoundToFormation(train, "sound_szsnd:ann.incarjp.sokusin");
            }

            // --- ドア制御 ---
            if (pushDoorSwitch) {
                Formation formation = train.getFormation();
                if (formation == null) {
                    formation = jp.ngt.rtm.entity.train.util.FormationManager.getInstance().createNewFormation(train);
                    train.setFormation(formation);
                }

                byte doorState = train.getTrainStateData(TrainStateType.State_Door.id);
                int trainDir = train.getTrainDirection();

                // 1. Local state (doorState) を Formation state に変換
                // RTMの実態： 1 (0x1) が左ドア、2 (0x2) が右ドア
                boolean isFormationLOpen, isFormationROpen;
                if (trainDir == 0) {
                    isFormationLOpen = (doorState & 0x1) == 0x1;
                    isFormationROpen = (doorState & 0x2) == 0x2;
                } else {
                    // 逆向きの場合、Localの左右とFormationの左右は逆転する
                    isFormationLOpen = (doorState & 0x2) == 0x2;
                    isFormationROpen = (doorState & 0x1) == 0x1;
                }

                // 2. cabIsLeft (プレイヤーから見たLocal Left) に基づいてFormation stateをトグル
                if (cabIsLeft) {
                    if (trainDir == 0) {
                        isFormationLOpen = !isFormationLOpen;
                    } else {
                        isFormationROpen = !isFormationROpen;
                    }
                } else {
                    if (trainDir == 0) {
                        isFormationROpen = !isFormationROpen;
                    } else {
                        isFormationLOpen = !isFormationLOpen;
                    }
                }

                // 3. Formation state を再構築して適用
                // RTMの実態： 1 (0x1) が左ドア、2 (0x2) が右ドアとして動作している
                int newDoorState = (isFormationLOpen ? 1 : 0) | (isFormationROpen ? 2 : 0);
                if (train.getFormation() != null) {
                    train.setTrainStateData(TrainStateType.State_Door.id, (byte) newDoorState);
                } else {
                    train.setTrainStateData_NoSync(TrainStateType.State_Door.id, (byte) newDoorState);
                }

                if (DEBUG) {
                    String msg = String.format(
                            "[Train #%d] \u00a7aドア操作実行\u00a7r: cabIsLeft=%b, trainDir=%d, 前doorState=%d -> 新doorState=%d (L開=%b, R開=%b)",
                            train.getEntityId(), cabIsLeft, trainDir, doorState, newDoorState, isFormationLOpen,
                            isFormationROpen);
                    sendDebugChat(train, msg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 編成全体に対してサウンドを再生する(旧JS playsound() 相当)。
     */
    private static void playSoundToFormation(EntityTrainBase train, String path) {
        try {
            String[] loc = path.split(":");
            if (loc.length != 2)
                return;
            ResourceLocation rLoc = new ResourceLocation(loc[0], loc[1]);
            Formation formation = train.getFormation();
            if (formation == null)
                return;
            for (int i = 0; i < formation.size(); i++) {
                FormationEntry fe = formation.get(i);
                if (fe != null && fe.train != null) {
                    RTMCore.proxy.playSound(fe.train, rLoc, 1.0f, 1.0f);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkInputKey(Object dataMap, Method getBoolean, Method setBoolean, String name,
            boolean isOnce) throws Exception {
        String kName = "suzu_keys_" + name;
        String prevName = kName + "_prev";
        boolean f1 = (Boolean) getBoolean.invoke(dataMap, kName);
        boolean f2 = (Boolean) getBoolean.invoke(dataMap, prevName);
        if (f1 != f2) {
            setBoolean.invoke(dataMap, prevName, f1, 3);
        }
        return isOnce ? (f1 && !f2) : f1;
    }

    // getTrainFromFormationEntry は CrossTieDMUtils に移管済み。

    // --- Sound ---
    private static class MelodyState {
        String dmain = null;
        String melo = null;
        int sustain = 0;
        boolean active = false;
    }

    private static class BuzzerState {
        boolean active = false;
        int sustain = 0;
    }

    private static final Map<String, MelodyState> melodyStates = new HashMap<>();
    private static final Map<String, BuzzerState> buzzerStates = new HashMap<>();

    public static void processConductorSound(Object su, Object dataMapObj, String buzzerPath, String bellPath,
            String dmain, String melo, float vol) {
        try {
            Method getEntity = su.getClass().getMethod("getEntity");
            Object entityObj = getEntity.invoke(su);
            if (!(entityObj instanceof EntityTrainBase))
                return;
            EntityTrainBase train = (EntityTrainBase) entityObj;

            String entityID = String.valueOf(train.getEntityId());

            Method getBoolean = dataMapObj.getClass().getMethod("getBoolean", String.class);
            boolean isBuzzer = (Boolean) getBoolean.invoke(dataMapObj, "suzu_isBuzzerFormation");
            boolean isBell = (Boolean) getBoolean.invoke(dataMapObj, "suzu_isBellFormation");
            boolean isMelody = (Boolean) getBoolean.invoke(dataMapObj, "suzu_isMelodyFormation");

            Method playSound = su.getClass().getMethod("playSound", String.class, String.class, float.class,
                    float.class, boolean.class);
            Method stopSound = su.getClass().getMethod("stopSound", String.class, String.class);

            // ブザー制御
            BuzzerState bState = buzzerStates.get(entityID);
            if (bState == null) {
                bState = new BuzzerState();
                buzzerStates.put(entityID, bState);
            }
            if (isBuzzer) {
                bState.sustain = 2;
            } else if (bState.sustain > 0) {
                bState.sustain--;
            }
            boolean playBuzzer = bState.sustain > 0;

            if (buzzerPath != null && !buzzerPath.isEmpty()) {
                String[] buzzerSnd = buzzerPath.split(":");
                if (buzzerSnd.length == 2) {
                    if (playBuzzer) {
                        if (!bState.active) {
                            playSound.invoke(su, buzzerSnd[0], buzzerSnd[1], 1.0f, 1.0f, true);
                            bState.active = true;
                        }
                    } else {
                        if (bState.active) {
                            stopSound.invoke(su, buzzerSnd[0], buzzerSnd[1]);
                            bState.active = false;
                        }
                    }
                }
            }

            // 電鈴制御
            if (isBell && bellPath != null && !bellPath.isEmpty()) {
                String[] loc = bellPath.split(":");
                if (loc.length == 2) {
                    ResourceLocation rLoc = new ResourceLocation(loc[0], loc[1]);
                    RTMCore.proxy.playSound(train, rLoc, 1.0f, 1.0f);
                }
            }

            // メロディ制御
            MelodyState mState = melodyStates.get(entityID);
            if (mState == null) {
                mState = new MelodyState();
                melodyStates.put(entityID, mState);
            }

            if (isMelody) {
                mState.sustain = 2;
                if (!melo.equals(mState.melo) || !dmain.equals(mState.dmain)) {
                    if (mState.melo != null && mState.dmain != null) {
                        stopSound.invoke(su, mState.dmain, mState.melo);
                    }
                    mState.dmain = dmain;
                    mState.melo = melo;
                    mState.active = false;
                }
                if (!mState.active) {
                    stopSound.invoke(su, "sound_szsnd", "ann.incarjp.sokusin");
                    playSound.invoke(su, dmain, melo, vol, 1.0f, true);
                    mState.active = true;
                }
            } else {
                if (mState.sustain > 0) {
                    mState.sustain--;
                } else if (mState.melo != null && mState.dmain != null) {
                    stopSound.invoke(su, mState.dmain, mState.melo);
                    mState.dmain = null;
                    mState.melo = null;
                    mState.active = false;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}