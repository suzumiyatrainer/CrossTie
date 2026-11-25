package net.suzumiya.crosstie.utils;

import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.train.util.FormationManager;
import jp.ngt.rtm.network.PacketFormation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * クライアント・サーバー間の {@link Formation} 同期補助ユーティリティ。
 *
 * <p>
 * {@code PacketFormation} がエンティティのスポーン前（ログイン直後やチャンク読込時など）に
 * クライアントへ届いた場合でも、エンティティID・編成ID・号車位置・向きを保持しておき、
 * 後からエンティティがスポーンした際や {@code getFormation()} / {@code Formation.get()} が
 * 呼ばれた際に自動的に編成と車両の双方向リンクを復元する。
 * </p>
 */
public final class FormationSyncHelper {

    /**
     * 編成ID -> 未解決エントリ一覧 (int[]{ trainEntityId, entryPos, entryDir })
     */
    private static final ConcurrentHashMap<Long, List<int[]>> PENDING_BY_FORMATION = new ConcurrentHashMap<>();

    /**
     * 車両エンティティID -> 最新編成情報 (long[]{ formationId, entryPos, entryDir, authoritative(1/0) })
     */
    private static final ConcurrentHashMap<Integer, long[]> TRAIN_TO_FORMATION = new ConcurrentHashMap<>();

    /**
     * クライアント側の編成同期バージョン。
     * {@code PacketFormation} 受信や自己修復が発生するたびにインクリメントされ、
     * {@code GuiTrainControlPanel} の編成タブ再構築トリガーとして利用される。
     */
    private static final AtomicLong CLIENT_SYNC_VERSION = new AtomicLong(0L);

    /**
     * サーバー側のトラッキング開始時送信デバウンス用キャッシュ。
     * キー: ((long) playerEntityId << 32) ^ formationId
     * 値: long[]{ timestampMs, formationSize, nonNullEntryCount }
     */
    private static final ConcurrentHashMap<Long, long[]> SERVER_SEND_DEBOUNCE = new ConcurrentHashMap<>();

    private static final long SERVER_SEND_DEBOUNCE_MS = 250L;

    /**
     * クライアント側の解決処理の再入防止ガード。
     * {@code getFormation()} 経由で間接的に再帰呼び出しが起きるのを確実に防ぐ。
     */
    private static final ThreadLocal<Boolean> IN_CLIENT_RESOLVE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private FormationSyncHelper() {
    }

    /**
     * クライアント側の編成同期バージョンを返す。
     */
    public static long getClientSyncVersion() {
        return CLIENT_SYNC_VERSION.get();
    }

    /**
     * クライアント側の編成状態が変化したことを通知する。
     */
    public static void notifyClientFormationUpdated() {
        CLIENT_SYNC_VERSION.incrementAndGet();
    }

    /**
     * クライアント側の未解決キャッシュをすべてクリアする（ワールド退出・再入場時用）。
     */
    public static void clearClientPending() {
        PENDING_BY_FORMATION.clear();
        TRAIN_TO_FORMATION.clear();
        CLIENT_SYNC_VERSION.incrementAndGet();
    }

    /**
     * サーバー側の送信デバウンスキャッシュをクリアする（ワールドアンロード時用）。
     */
    public static void clearServerDebounce() {
        SERVER_SEND_DEBOUNCE.clear();
    }

    /**
     * サーバー側から特定プレイヤーへ対象車両の編成データを {@link PacketFormation} で送信する。
     *
     * @param train  対象車両
     * @param player 送信先プレイヤー
     * @param force  true の場合はデバウンスを無視して即座に送信する（GUIオープン・タブ切替時など）
     */
    public static void sendFormationToPlayer(EntityTrainBase train, EntityPlayerMP player, boolean force) {
        if (train == null || player == null || train.worldObj == null || train.worldObj.isRemote) {
            return;
        }
        Formation formation = train.getFormation();
        if (formation == null || formation.entries == null || formation.entries.length == 0) {
            return;
        }

        int size = formation.entries.length;
        int nonNullCount = 0;
        for (int i = 0; i < size; i++) {
            FormationEntry e = formation.entries[i];
            if (e != null && e.train != null && !e.train.isDead) {
                nonNullCount++;
            }
        }
        if (nonNullCount == 0) {
            return;
        }

        long key = (((long) player.getEntityId()) << 32) ^ (formation.id * 0x9E3779B97F4A7C15L);
        long now = System.currentTimeMillis();
        if (!force) {
            long[] last = SERVER_SEND_DEBOUNCE.get(key);
            if (last != null
                    && (now - last[0]) < SERVER_SEND_DEBOUNCE_MS
                    && last[1] == size
                    && last[2] == nonNullCount) {
                return;
            }
        }

        if (SERVER_SEND_DEBOUNCE.size() > 4096) {
            SERVER_SEND_DEBOUNCE.clear();
        }
        SERVER_SEND_DEBOUNCE.put(key, new long[] { now, size, nonNullCount });

        try {
            RTMCore.NETWORK_WRAPPER.sendTo(new PacketFormation(formation), player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * {@code Formation.readFromNBT} (PacketFormation 受信) 時に車両エンティティと編成の紐付けを確定情報として記録する。
     */
    public static void recordFormationEntry(long formationId, int trainId, int pos, int dir, boolean resolved) {
        TRAIN_TO_FORMATION.put(trainId, new long[] { formationId, pos, dir, 1L });
        if (!resolved) {
            List<int[]> list = PENDING_BY_FORMATION.computeIfAbsent(formationId, k -> new ArrayList<>());
            synchronized (list) {
                // 同一位置または同一車両IDの古い予約を置換
                for (Iterator<int[]> it = list.iterator(); it.hasNext();) {
                    int[] existing = it.next();
                    if (existing[0] == trainId || existing[1] == pos) {
                        it.remove();
                    }
                }
                list.add(new int[] { trainId, pos, dir });
            }
        }
    }

    /**
     * {@code PacketNBT} 受信時に、まだ {@code PacketFormation} による確定情報が無い車両の編成情報をフォールバック記録する。
     */
    public static void recordNbtEntryIfAbsent(long formationId, int trainId, int pos, int dir) {
        long[] existing = TRAIN_TO_FORMATION.get(trainId);
        if (existing != null && existing.length >= 4 && existing[3] == 1L) {
            // PacketFormation による確定情報を優先
            return;
        }
        TRAIN_TO_FORMATION.put(trainId, new long[] { formationId, pos, dir, 0L });
    }

    /**
     * 指定車両について、{@code PacketFormation} による確定マッピングが既に存在するかを返す。
     */
    public static boolean hasAuthoritativeMapping(int trainId) {
        long[] mapped = TRAIN_TO_FORMATION.get(trainId);
        return mapped != null && mapped.length >= 4 && mapped[3] == 1L;
    }

    /**
     * 指定車両が現在マッピングされている編成IDを返す。未登録の場合は {@code Long.MIN_VALUE}。
     */
    public static long getMappedFormationId(int trainId) {
        long[] mapped = TRAIN_TO_FORMATION.get(trainId);
        return (mapped != null && mapped.length >= 1) ? mapped[0] : Long.MIN_VALUE;
    }

    /**
     * 指定編成から外れた古い車両マッピング（確定済み編成のサイズ超過・同一スロットの別車両残骸など）を整理する。
     */
    public static void pruneStaleEntriesForFormation(long formationId, int newSize, int[] receivedTrainIdsByPos) {
        for (Iterator<Map.Entry<Integer, long[]>> it = TRAIN_TO_FORMATION.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, long[]> e = it.next();
            long[] val = e.getValue();
            if (val != null && val[0] == formationId) {
                int pos = (int) val[1];
                if (pos < 0 || pos >= newSize) {
                    it.remove();
                } else if (receivedTrainIdsByPos != null && pos < receivedTrainIdsByPos.length) {
                    int expectedTrainId = receivedTrainIdsByPos[pos];
                    if (expectedTrainId != Integer.MIN_VALUE && expectedTrainId != e.getKey()) {
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * 指定編成の未解決リストを置き換える。
     */
    public static void setPendingForFormation(long formationId, List<int[]> pending) {
        if (pending == null || pending.isEmpty()) {
            PENDING_BY_FORMATION.remove(formationId);
        } else {
            PENDING_BY_FORMATION.put(formationId, pending);
        }
    }

    /**
     * クライアント側で対象編成の指定インデックス {@code targetPos} が {@code null} の場合に自己修復を試みる。
     */
    public static FormationEntry tryHealEntry(Formation formation, int targetPos) {
        if (formation == null || formation.entries == null || targetPos < 0 || targetPos >= formation.entries.length) {
            return null;
        }
        try {
            if (NGTUtil.isServer()) {
                return null;
            }
            World world = NGTUtil.getClientWorld();
            if (world == null) {
                return null;
            }

            // 1) PacketFormation で受信済みだった未解決エントリから復元
            List<int[]> pending = PENDING_BY_FORMATION.get(formation.id);
            if (pending != null) {
                synchronized (pending) {
                    for (Iterator<int[]> it = pending.iterator(); it.hasNext();) {
                        int[] p = it.next();
                        if (p[1] != targetPos) {
                            continue;
                        }
                        Entity e = world.getEntityByID(p[0]);
                        if (e instanceof EntityTrainBase && !e.isDead) {
                            EntityTrainBase train = (EntityTrainBase) e;
                            it.remove();
                            FormationEntry created = new FormationEntry(train, p[1], p[2]);
                            formation.entries[targetPos] = created;
                            train.setFormation(formation);
                            CLIENT_SYNC_VERSION.incrementAndGet();
                            return created;
                        }
                    }
                    if (pending.isEmpty()) {
                        PENDING_BY_FORMATION.remove(formation.id);
                    }
                }
            }

            // 2) クライアントワールド内のロード済み車両から該当編成・位置の車両を探索
            List<?> loaded = world.loadedEntityList;
            if (loaded != null) {
                for (int i = 0; i < loaded.size(); i++) {
                    Object obj = loaded.get(i);
                    if (obj instanceof EntityTrainBase) {
                        EntityTrainBase train = (EntityTrainBase) obj;
                        if (train.isDead) {
                            continue;
                        }
                        long[] mapped = TRAIN_TO_FORMATION.get(train.getEntityId());
                        if (mapped != null && mapped[0] == formation.id && (int) mapped[1] == targetPos) {
                            FormationEntry created = new FormationEntry(train, targetPos, (byte) mapped[2]);
                            formation.entries[targetPos] = created;
                            train.setFormation(formation);
                            CLIENT_SYNC_VERSION.incrementAndGet();
                            return created;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * クライアント側で編成の全未解決エントリを一括解決する。
     */
    public static void resolveAllPendingForFormation(Formation formation) {
        if (formation == null || formation.entries == null) {
            return;
        }
        for (int i = 0; i < formation.entries.length; i++) {
            FormationEntry entry = formation.entries[i];
            if (entry == null || entry.train == null || entry.train.isDead) {
                tryHealEntry(formation, i);
            } else {
                // getFormation() は Mixin フック経由で resolveClientTrainFormation を再帰呼び出しするため呼ばず、
                // 直接 setFormation() で参照を揃える
                entry.train.setFormation(formation);
            }
        }
    }

    /**
     * クライアント側で {@link EntityTrainBase} が属すべき最新の {@link Formation} を解決・同期する。
     */
    public static Formation resolveClientTrainFormation(EntityTrainBase train, Formation currentFormation) {
        if (train == null || Boolean.TRUE.equals(IN_CLIENT_RESOLVE.get())) {
            return currentFormation;
        }
        IN_CLIENT_RESOLVE.set(Boolean.TRUE);
        try {
            if (NGTUtil.isServer()) {
                return currentFormation;
            }
            int entityId = train.getEntityId();
            FormationManager fm = FormationManager.getInstance();
            long[] mapped = TRAIN_TO_FORMATION.get(entityId);
            if (mapped != null && fm != null) {
                long targetFormationId = mapped[0];
                int pos = (int) mapped[1];
                byte dir = (byte) mapped[2];

                Formation target = fm.getFormation(targetFormationId);
                if (target != null) {
                    if (target.entries != null && pos >= 0 && pos < target.entries.length) {
                        FormationEntry entry = target.entries[pos];
                        if (entry == null || entry.train != train) {
                            entry = new FormationEntry(train, pos, dir);
                            target.entries[pos] = entry;
                            CLIENT_SYNC_VERSION.incrementAndGet();
                        } else {
                            entry.entryId = (byte) pos;
                            entry.dir = dir;
                        }
                    }
                    resolveAllPendingForFormation(target);
                    return target;
                }
            }

            if (currentFormation != null && fm != null) {
                // FormationManager 側に同一IDの新しい Formation インスタンスがあればそちらへ追従
                Formation latest = fm.getFormation(currentFormation.id);
                if (latest != null && latest != currentFormation) {
                    resolveAllPendingForFormation(latest);
                    return latest;
                }
                resolveAllPendingForFormation(currentFormation);
            }
        } catch (Exception ignored) {
        } finally {
            IN_CLIENT_RESOLVE.set(Boolean.FALSE);
        }
        return currentFormation;
    }
}
