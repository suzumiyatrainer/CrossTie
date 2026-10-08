package net.suzumiya.crosstie.physics;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

public class PhysicsEngine {
    /** 物理計算用のスレッドプール。並列度合いは環境のコア数に依存 */
    public static final ForkJoinPool POOL = new ForkJoinPool();

    /** 現在のスレッドがバックグラウンド計算中かどうか */
    public static final ThreadLocal<Boolean> isWorkerThread = ThreadLocal.withInitial(() -> false);

    /** サウンド再生要求を格納するキュー。ワーカースレッドから積まれ、メインスレッドで消化される */
    public static final ConcurrentLinkedQueue<Runnable> soundQueue = new ConcurrentLinkedQueue<>();

    /** 現在の tick で物理計算が完了した World (ディメンション) とそのエポック (tick) を記録 */
    private static int lastPhysicsTick = -1;
    private static World lastPhysicsWorld = null;

    /**
     * Eager Global Physics Update のエントリポイント。 各 tick で最初に呼び出された列車の updateMovement()
     * から発火する。
     */
    public static void ensureGlobalPhysics(World world, int currentTick) {
        if (world.isRemote) {
            return; // ひとまずサーバー側専用
        }

        if (lastPhysicsTick == currentTick && lastPhysicsWorld == world) {
            return; // 既にこの tick のこのワールドの計算は終わっている
        }

        lastPhysicsTick = currentTick;
        lastPhysicsWorld = world;

        doGlobalPhysics(world);
    }

    private static void doGlobalPhysics(World world) {
        try {
            // 1. 全 Formation の収集
            FormationManager fm = FormationManager.getInstance();
            if (fm == null)
                return;
            Map<Long, Formation> formationMap = fm.getFormations();
            if (formationMap == null || formationMap.isEmpty())
                return;

            List<FormationPhysicsTask> tasks = new ArrayList<>();

            for (Formation formation : formationMap.values()) {
                if (formation == null || formation.entries == null || formation.entries.length == 0)
                    continue;
                // そのワールドに属する編成だけを対象にする
                EntityTrainBase firstTrain = findFirstTrain(formation);
                if (firstTrain != null && firstTrain.worldObj == world && !firstTrain.isDead) {
                    tasks.add(new FormationPhysicsTask(formation));
                }
            }

            if (tasks.isEmpty())
                return;

            // 2. 並列計算の実行 (結果はエンティティに直接書き込まれる)
            POOL.invokeAll(tasks);

            // 3. 遅延された副作用（サウンドなど）の適用 (メインスレッド)
            Runnable task;
            while ((task = soundQueue.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    // 個別のサウンドタスク等の失敗で全体のループを止めない
                }
            }
        } catch (Throwable t) {
            System.err.println("[CrossTie-PhysicsEngine] Error in doGlobalPhysics: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static EntityTrainBase findFirstTrain(Formation formation) {
        if (formation == null || formation.entries == null) {
            return null;
        }
        for (int i = 0; i < formation.entries.length; i++) {
            if (formation.entries[i] != null && formation.entries[i].train != null) {
                return formation.entries[i].train;
            }
        }
        return null;
    }
}
