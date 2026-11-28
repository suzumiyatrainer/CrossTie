package net.suzumiya.crosstie.mixins.ngtlib;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import net.suzumiya.crosstie.client.GLHelperCleanupHandler;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 2-2: {@link GLHelper} の DisplayList 無限リーク、O(N) 削除、Use-After-Free、二重解放を根治する Mixin。
 *
 * <ul>
 *   <li>{@code activeGLLists} を {@link IdentityHashMap} ベース {@link Set} に置き換えて O(1) 化。</li>
 *   <li>{@code deleteGLList()} は即座に {@code dl.value = 0} とし、{@code int} ID を重複排除セットへ積むだけ
 *       （GL コール無し・Netty/別スレッドから呼ばれても安全・二重解放 100% 防止）。</li>
 *   <li>{@code generateGLList(DisplayList)} は削除キュー内 ID の再利用を禁止し、再利用時は旧ハンドルを無効化
 *       （Use-After-Free 100% 防止）。</li>
 *   <li>{@code clearGLList()} はロック外で {@code GL11.glDeleteLists} を実行（Angelica {@code acquireDrawLock} とのデッドロック回避）。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = GLHelper.class, remap = false)
public abstract class GLHelperCleanupMixin {

    @Unique
    private static final Object CROSSTIE_LIST_LOCK = new Object();

    @Unique
    private static final Set<DisplayList> CROSSTIE_ACTIVE_LISTS =
            Collections.newSetFromMap(new IdentityHashMap<DisplayList, Boolean>());

    @Unique
    private static final LinkedHashSet<Integer> CROSSTIE_PENDING_DELETE_IDS = new LinkedHashSet<Integer>();

    /** 3-3: ThreadLocal<Integer> のボクシング＆remove()再初期化を排除する非ボクシング深度カウンタ */
    @Unique
    private static final ThreadLocal<int[]> CROSSTIE_COMPILE_DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    @Unique
    private static final Constructor<DisplayList> CROSSTIE_DL_CTOR;

    static {
        Constructor<DisplayList> ctor = null;
        try {
            ctor = DisplayList.class.getDeclaredConstructor(int.class);
            ctor.setAccessible(true);
        } catch (Throwable t) {
            throw new RuntimeException("[CrossTie] Failed to access DisplayList(int) constructor", t);
        }
        CROSSTIE_DL_CTOR = ctor;

        GLHelperCleanupHandler.registerCallbacks(
                () -> crosstie$clearGLListBatch(GLHelperCleanupHandler.getMaxDeletePerFrame()),
                () -> crosstie$clearGLListBatch(-1)
        );
    }

    @Unique
    private static DisplayList crosstie$newDisplayList(int id) {
        try {
            return CROSSTIE_DL_CTOR.newInstance(id);
        } catch (Throwable t) {
            throw new RuntimeException("[CrossTie] Failed to instantiate DisplayList", t);
        }
    }

    @Unique
    private static int[] crosstie$drainPendingIdsLocked(int maxBatch) {
        int total = CROSSTIE_PENDING_DELETE_IDS.size();
        if (total == 0) {
            return null;
        }
        int count = (maxBatch <= 0 || maxBatch >= total) ? total : maxBatch;
        int[] batch = new int[count];
        Iterator<Integer> it = CROSSTIE_PENDING_DELETE_IDS.iterator();
        for (int i = 0; i < count && it.hasNext(); i++) {
            batch[i] = it.next();
            it.remove();
        }
        return batch;
    }

    @Unique
    private static void crosstie$clearGLListBatch(int maxBatch) {
        if (GLHelper.isCompiling()) {
            return;
        }
        int[] batch;
        synchronized (CROSSTIE_LIST_LOCK) {
            batch = crosstie$drainPendingIdsLocked(maxBatch);
        }
        if (batch == null) {
            return;
        }
        for (int i = 0; i < batch.length; i++) {
            int id = batch[i];
            if (id > 0 && GL11.glIsList(id)) {
                GL11.glDeleteLists(id, 1);
            }
        }
    }

    /**
     * @author CrossTie
     * @reason Use-After-Free / 二重解放を防ぎつつ安全に DisplayList を解放する。
     */
    @Overwrite
    public static void clearGLList() {
        crosstie$clearGLListBatch(-1);
    }

    /**
     * @author CrossTie
     * @reason 全アクティブ DisplayList を即座に無効化し、ロック外で一括解放する。
     */
    @Overwrite
    public static void initGLList() {
        int[] allIds;
        synchronized (CROSSTIE_LIST_LOCK) {
            for (DisplayList dl : CROSSTIE_ACTIVE_LISTS) {
                if (dl != null && dl.value > 0) {
                    CROSSTIE_PENDING_DELETE_IDS.add(dl.value);
                    dl.value = 0;
                }
            }
            CROSSTIE_ACTIVE_LISTS.clear();
            allIds = crosstie$drainPendingIdsLocked(-1);
        }
        if (allIds == null) {
            return;
        }
        for (int i = 0; i < allIds.length; i++) {
            int id = allIds[i];
            if (id > 0 && GL11.glIsList(id)) {
                GL11.glDeleteLists(id, 1);
            }
        }
    }

    /**
     * @author CrossTie
     * @reason O(1) 削除、即時 value=0 化による二重解放防止、GL コール無しでスレッドセーフ化。
     */
    @Overwrite
    public static void deleteGLList(DisplayList par1) {
        if (par1 == null) {
            return;
        }
        synchronized (CROSSTIE_LIST_LOCK) {
            int id = par1.value;
            if (id <= 0) {
                return;
            }
            par1.value = 0;
            CROSSTIE_ACTIVE_LISTS.remove(par1);
            CROSSTIE_PENDING_DELETE_IDS.add(id);
        }
    }

    /**
     * @author CrossTie
     * @reason IdentityHashMap ベースの O(1) アクティブ管理へ統一。
     */
    @Overwrite
    public static DisplayList generateGLList() {
        int id = GL11.glGenLists(1);
        DisplayList list = crosstie$newDisplayList(id);
        synchronized (CROSSTIE_LIST_LOCK) {
            CROSSTIE_ACTIVE_LISTS.add(list);
        }
        return list;
    }

    /**
     * @author CrossTie
     * @reason 削除キュー内 ID の再利用禁止と旧ハンドル無効化により Use-After-Free を完全防止。
     */
    @Overwrite
    public static DisplayList generateGLList(@Nullable DisplayList par1) {
        int reuseId = 0;
        if (par1 != null) {
            synchronized (CROSSTIE_LIST_LOCK) {
                int candidate = par1.value;
                if (candidate > 0 && !CROSSTIE_PENDING_DELETE_IDS.contains(candidate)) {
                    reuseId = candidate;
                    par1.value = 0;
                    CROSSTIE_ACTIVE_LISTS.remove(par1);
                }
            }
        }

        int finalId = (reuseId > 0 && GL11.glIsList(reuseId)) ? reuseId : GL11.glGenLists(1);
        DisplayList list = crosstie$newDisplayList(finalId);
        synchronized (CROSSTIE_LIST_LOCK) {
            CROSSTIE_ACTIVE_LISTS.add(list);
        }
        return list;
    }

    /**
     * @author CrossTie
     * @reason 毎パーツ呼ばれる isCompiling() の Integer ボクシングを排除。
     */
    @Overwrite
    public static boolean isCompiling() {
        return CROSSTIE_COMPILE_DEPTH.get()[0] > 0;
    }

    /**
     * @author CrossTie
     * @reason 非ボクシング深度インクリメント。
     */
    @Overwrite
    private static void incrementCompileDepth() {
        CROSSTIE_COMPILE_DEPTH.get()[0]++;
    }

    /**
     * @author CrossTie
     * @reason 非ボクシング深度デクリメント（ThreadLocal.remove() による再初期化コストを排除）。
     */
    @Overwrite
    private static void decrementCompileDepth() {
        int[] depth = CROSSTIE_COMPILE_DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }
}
