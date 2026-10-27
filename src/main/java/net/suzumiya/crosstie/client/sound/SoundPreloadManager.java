package net.suzumiya.crosstie.client.sound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.ModelPackManager;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.CrossTieConfig;

/**
 * RTM 車両・ホームドア等のサウンド未ロード起因による開始遅延（初回再生ラグ）を解消する
 * サウンドプリロード（事前ウォームアップ）マネージャー。
 *
 * <p>
 * Minecraft の Paulscode SoundSystem に対し、あらかじめ無音の PreloadSound を通すことで、
 * ディスク I/O と Ogg デコードを事前完了させ、OpenAL バッファにキャッシュします。
 * これにより、ドア開閉時やチャイムの初回再生ラグを 0ms に抑え込みます。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class SoundPreloadManager {

    private static final Set<String> PRELOADED_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 指定されたサウンドリソースを Paulscode SoundSystem に事前ロードする。
     */
    public static void preload(ResourceLocation sound) {
        if (!CrossTieConfig.enableSoundPreload || sound == null) {
            return;
        }

        String key = sound.getResourceDomain() + ":" + sound.getResourcePath();
        if (!PRELOADED_KEYS.add(key)) {
            return; // 既にプリロード済み
        }

        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.getSoundHandler() != null) {
                mc.getSoundHandler().playSound(new PreloadSound(sound));
                if (CrossTieConfig.enableSoundDebug) {
                    CrossTie.LOGGER.info("[CrossTie-Sound] Preloaded sound: {}", key);
                }
            }
        } catch (Throwable t) {
            if (CrossTieConfig.enableSoundDebug) {
                CrossTie.LOGGER.warn("[CrossTie-Sound] Failed to preload sound {}: {}", key, t.getMessage());
            }
        }
    }

    /**
     * 文字列指定のサウンド（"domain:path" または "domain", "path"）を事前ロードする。
     */
    public static void preload(String domain, String path) {
        if (domain == null || path == null) {
            return;
        }
        preload(new ResourceLocation(domain, path));
    }

    public static void preload(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        String[] split = path.split(":");
        if (split.length == 2) {
            preload(new ResourceLocation(split[0], split[1]));
        } else {
            preload(new ResourceLocation("minecraft", path));
        }
    }

    /**
     * 指定された列車のモデルセットに含まれる全サウンド（ドア開閉、チャイム、警笛、ブレーキ等）をプリロードする。
     */
    public static void preloadTrain(EntityTrainBase train) {
        if (!CrossTieConfig.enableSoundPreload || train == null) {
            return;
        }

        try {
            ModelSetVehicleBase<?> modelSet = train.getModelSet();
            if (modelSet != null) {
                preloadModel(modelSet);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * モデルセットからドア音および関連効果音を抽出してプリロードする。
     */
    public static void preloadModel(ModelSetVehicleBase<?> modelSet) {
        if (!CrossTieConfig.enableSoundPreload || modelSet == null) {
            return;
        }

        // 1. ドア開閉音（最重要）
        preload(modelSet.sound_doorOpen);
        preload(modelSet.sound_doorClose);

        // 2. クライアント専用モデルセットの効果音
        if (modelSet instanceof ModelSetVehicleBaseClient) {
            ModelSetVehicleBaseClient<?> clientModel = (ModelSetVehicleBaseClient<?>) modelSet;
            preload(clientModel.sound_Horn);
            preload(clientModel.sound_ATSChime);
            preload(clientModel.sound_ATSBell);
            preload(clientModel.sound_Stop);
        }

        // 3. 設定ファイル由来のサウンド
        try {
            VehicleBaseConfig cfg = modelSet.getConfig();
            if (cfg != null) {
                if (cfg.sound_Announcement != null) {
                    for (String[] ann : cfg.sound_Announcement) {
                        if (ann != null && ann.length >= 2) {
                            preload(ann[1]);
                        }
                    }
                }
                if (cfg instanceof TrainConfig) {
                    TrainConfig tcfg = (TrainConfig) cfg;
                    preload(tcfg.sound_BrakeRelease);
                    preload(tcfg.sound_BrakeRelease2);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * RTM に登録されている全列車モデルのドア開閉音・効果音を一括事前ロードする。
     */
    public static void preloadAllRegisteredModels() {
        if (!CrossTieConfig.enableSoundPreload) {
            return;
        }

        try {
            ModelPackManager mpm = ModelPackManager.INSTANCE;
            if (mpm == null) {
                return;
            }

            // Train モデルの全モデルセットを巡回
            for (ModelSetBase<?> set : mpm.getModelList("ModelTrain")) {
                if (set instanceof ModelSetVehicleBase) {
                    preloadModel((ModelSetVehicleBase<?>) set);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ワールド参加時に全列車モデルのサウンドを一括プリロードするためのイベントリスナー。
     */
    public static class SoundPreloadEventHandler {
        private static boolean hasPreloadedAll = false;

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onEntityJoinWorld(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
            if (event.world.isRemote && event.entity == Minecraft.getMinecraft().thePlayer) {
                if (!hasPreloadedAll) {
                    hasPreloadedAll = true;
                    preloadAllRegisteredModels();
                }
            }
        }
    }
}
