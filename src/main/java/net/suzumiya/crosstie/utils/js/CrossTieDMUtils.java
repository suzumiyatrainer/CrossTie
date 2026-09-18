package net.suzumiya.crosstie.utils.js;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;

/**
 * DataMap操作に関する共通ユーティリティ。
 *
 * <ul>
 * <li>Throttle Lib (JS版 suzu_setThrottled* 相当) : 値に変化が無ければSYNCフラグを立てずに
 * 書き込むことで、無駄なパケット送信を抑制する。</li>
 * <li>編成間DataMap同期 : コントロールカーのDataMapに対して、呼び出し側(JS/Java)から渡された
 * キー名リスト(int/boolean/string別)を対象に、編成内の全車両へ値を伝播する。 キー名をハードコードせず、
 * 呼び出し側が同期対象を自由に指定できる汎用実装。</li>
 * </ul>
 */
public final class CrossTieDMUtils {

    private CrossTieDMUtils() {
    }

    // ==========================================================
    // --- Throttle Lib (JS準拠) ---
    // 値に変化がない場合はSYNCビット(flag & 1)を落として書き込み、
    // 無駄な同期パケット送信を抑制する。
    // ==========================================================

    public static void setThrottledBoolean(Object dataMap, Method getBoolean, Method setBoolean, String key,
            boolean value, int flag) throws Exception {
        if (dataMap == null)
            return;
        if ((flag & 1) != 0) {
            boolean current = (Boolean) getBoolean.invoke(dataMap, key);
            if (current != value) {
                setBoolean.invoke(dataMap, key, value, flag);
            } else {
                setBoolean.invoke(dataMap, key, value, flag & ~1);
            }
        } else {
            setBoolean.invoke(dataMap, key, value, flag);
        }
    }

    public static void setThrottledInt(Object dataMap, Method getInt, Method setInt, String key, int value, int flag)
            throws Exception {
        if (dataMap == null)
            return;
        if ((flag & 1) != 0) {
            int current = (Integer) getInt.invoke(dataMap, key);
            if (current != value) {
                setInt.invoke(dataMap, key, value, flag);
            } else {
                setInt.invoke(dataMap, key, value, flag & ~1);
            }
        } else {
            setInt.invoke(dataMap, key, value, flag);
        }
    }

    public static void setThrottledString(Object dataMap, Method getString, Method setString, String key, String value,
            int flag) throws Exception {
        if (dataMap == null)
            return;
        String valStr = String.valueOf(value);
        String currentStr = String.valueOf(getString.invoke(dataMap, key));
        if (!currentStr.equals(valStr)) {
            setString.invoke(dataMap, key, valStr, flag);
        }
    }

    // ==========================================================
    // --- 編成間 DataMap 同期 (汎用) ---
    // ==========================================================

    /**
     * コントロールカー(localDataMap)の値を基準に、渡されたキー名リスト(int/boolean/string別)を
     * 編成内の全車両のDataMapへ伝播する。キー名はJS/Java呼び出し側から動的に渡されるため、 このメソッド自体は特定のキー名を一切知らない汎用実装。
     *
     * 変化検知は "last_sync_" + key という補助キーに前回同期値を保持することで行う。 trainTick % 100 == 0
     * のタイミングでは変化の有無に関わらず強制的に再同期する (マルチプレイでのパケット欠損/遅延対策)。
     *
     * @param controlCar   編成の基準となる車両(コントロールカー)
     * @param localDataMap controlCarのDataMap
     * @param intKeys      同期対象のintキー名 (nullまたは空配列可)
     * @param boolKeys     同期対象のbooleanキー名 (nullまたは空配列可)
     * @param stringKeys   同期対象のstringキー名 (nullまたは空配列可)
     */
    public static void syncFormationKeys(EntityTrainBase controlCar, Object localDataMap, Method getInt, Method setInt,
            Method getBoolean, Method setBoolean, Method getString, Method setString, int trainTick, String[] intKeys,
            String[] boolKeys, String[] stringKeys) throws Exception {
        if (controlCar == null || localDataMap == null)
            return;

        Formation formation = controlCar.getFormation();
        if (formation == null)
            return;

        boolean forceSync = (trainTick % 100 == 0);

        // --- int ---
        List<String> changedIntKeys = new ArrayList<>();
        List<Integer> changedIntVals = new ArrayList<>();
        if (intKeys != null) {
            for (String key : intKeys) {
                int controlValue = (Integer) getInt.invoke(localDataMap, key);
                int lastValue = (Integer) getInt.invoke(localDataMap, "last_sync_" + key);
                if (controlValue != lastValue || forceSync) {
                    changedIntKeys.add(key);
                    changedIntVals.add(controlValue);
                }
            }
        }

        // --- boolean ---
        List<String> changedBoolKeys = new ArrayList<>();
        List<Boolean> changedBoolVals = new ArrayList<>();
        if (boolKeys != null) {
            for (String key : boolKeys) {
                boolean controlValue = (Boolean) getBoolean.invoke(localDataMap, key);
                boolean lastValue = (Boolean) getBoolean.invoke(localDataMap, "last_sync_" + key);
                if (controlValue != lastValue || forceSync) {
                    changedBoolKeys.add(key);
                    changedBoolVals.add(controlValue);
                }
            }
        }

        // --- string ---
        List<String> changedStringKeys = new ArrayList<>();
        List<String> changedStringVals = new ArrayList<>();
        if (stringKeys != null) {
            for (String key : stringKeys) {
                String controlValue = String.valueOf(getString.invoke(localDataMap, key));
                String lastValue = String.valueOf(getString.invoke(localDataMap, "last_sync_" + key));
                if (!controlValue.equals(lastValue) || forceSync) {
                    changedStringKeys.add(key);
                    changedStringVals.add(controlValue);
                }
            }
        }

        if (changedIntKeys.isEmpty() && changedBoolKeys.isEmpty() && changedStringKeys.isEmpty())
            return;

        for (int i = 0; i < formation.size(); i++) {
            EntityTrainBase fEntity = getTrainFromFormationEntry(formation.get(i));
            if (fEntity == null)
                continue;
            Object targetDM = getFormationDataMap(fEntity);
            if (targetDM == null)
                continue;

            for (int k = 0; k < changedIntKeys.size(); k++) {
                String key = changedIntKeys.get(k);
                int val = changedIntVals.get(k);
                if ((Integer) getInt.invoke(targetDM, key) != val || forceSync) {
                    setThrottledInt(targetDM, getInt, setInt, key, val, 3);
                }
            }
            for (int k = 0; k < changedBoolKeys.size(); k++) {
                String key = changedBoolKeys.get(k);
                boolean val = changedBoolVals.get(k);
                if ((Boolean) getBoolean.invoke(targetDM, key) != val || forceSync) {
                    setThrottledBoolean(targetDM, getBoolean, setBoolean, key, val, 3);
                }
            }
            for (int k = 0; k < changedStringKeys.size(); k++) {
                String key = changedStringKeys.get(k);
                String val = changedStringVals.get(k);
                String cur = String.valueOf(getString.invoke(targetDM, key));
                if (!cur.equals(val) || forceSync) {
                    setThrottledString(targetDM, getString, setString, key, val, 3);
                }
            }
        }

        for (int k = 0; k < changedIntKeys.size(); k++) {
            setInt.invoke(localDataMap, "last_sync_" + changedIntKeys.get(k), changedIntVals.get(k), 0);
        }
        for (int k = 0; k < changedBoolKeys.size(); k++) {
            setBoolean.invoke(localDataMap, "last_sync_" + changedBoolKeys.get(k), changedBoolVals.get(k), 0);
        }
        for (int k = 0; k < changedStringKeys.size(); k++) {
            setString.invoke(localDataMap, "last_sync_" + changedStringKeys.get(k), changedStringVals.get(k), 0);
        }
    }

    /**
     * 編成内の全車両へ単一のboolean値を無条件で反映する(集約フラグの配信用)。 例: 編成内いずれかの車両がブザー鳴動中かどうかを示す
     * suzu_isBuzzerFormation 等、 呼び出し側で既に集約済みの値をそのまま全車へ配るケースに使用する。
     */
    public static void broadcastBooleanToFormation(Formation formation, Method getBoolean, Method setBoolean,
            String key, boolean value) throws Exception {
        if (formation == null)
            return;
        for (int i = 0; i < formation.size(); i++) {
            EntityTrainBase fEntity = getTrainFromFormationEntry(formation.get(i));
            if (fEntity == null)
                continue;
            Object targetDM = getFormationDataMap(fEntity);
            if (targetDM == null)
                continue;
            setThrottledBoolean(targetDM, getBoolean, setBoolean, key, value, 3);
        }
    }

    /**
     * 編成エントリ(FormationEntry等)からEntityTrainBaseを取得する。 FormationEntry.train
     * はリフレクションではなくpublicフィールドとして直接公開されているため、 getTrain()のようなメソッド呼び出しは行わないこと(存在しない)。
     */
    public static EntityTrainBase getTrainFromFormationEntry(Object entry) {
        if (entry instanceof EntityTrainBase)
            return (EntityTrainBase) entry;
        if (entry == null)
            return null;
        try {
            Field trainField = entry.getClass().getField("train");
            return (EntityTrainBase) trainField.get(entry);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 編成メンバー車両のDataMapをリフレクション経由で取得する。
     */
    public static Object getFormationDataMap(EntityTrainBase fEntity) {
        try {
            Method getResourceState = fEntity.getClass().getMethod("getResourceState");
            Object rState = getResourceState.invoke(fEntity);
            Method getDataMap = rState.getClass().getMethod("getDataMap");
            return getDataMap.invoke(rState);
        } catch (Exception e) {
            return null;
        }
    }
}