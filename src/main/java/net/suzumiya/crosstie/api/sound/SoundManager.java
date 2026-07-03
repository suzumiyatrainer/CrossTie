package net.suzumiya.crosstie.api.sound;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * CrossTie Sound API Manager
 * 1文字でも既存実装を壊さず、極限まで軽量に設計されたシングルトンAPI
 */
public class SoundManager {
    
    private static final SoundManager INSTANCE = new SoundManager();
    
    // 軽量化のためWeakHashMapを使用し、車両がデスポーンした際のメモリリークを防止
    private final Map<EntityTrainBase, TrainSoundContext> contextMap = new WeakHashMap<>();

    private SoundManager() {}

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    public TrainSoundContext getContext(EntityTrainBase train) {
        return contextMap.computeIfAbsent(train, TrainSoundContext::new);
    }

    /**
     * ノッチが変化した際にMixinから呼ばれる
     */
    public void onNotchChanged(EntityTrainBase train, int notch) {
        TrainSoundContext ctx = getContext(train);
        ctx.setNotch(notch);
    }

    /**
     * ドア状態が変化した際にMixinから呼ばれる
     */
    public void onDoorStateChanged(EntityTrainBase train, byte doorState) {
        TrainSoundContext ctx = getContext(train);
        ctx.setDoorState(doorState);
    }

    /**
     * 速度が変化した際にMixinから呼ばれる
     */
    public void onSpeedChanged(EntityTrainBase train, float speed) {
        TrainSoundContext ctx = getContext(train);
        ctx.setSpeed(speed);
    }

    public void playCustomSound(EntityTrainBase train, String soundName) {
        SoundQueueManager.getInstance().enqueueTrainSound(train.getEntityId(), 20.0f, 30.0f, new String[]{soundName}, new int[]{0}, (byte)0);
    }
    
    // --- JavaScript Advanced APIs ---

    public void playInCarAnnouncement(EntityTrainBase train, float length, Object soundsObj, Object delaysObj) {
        playInCarAnnouncement(train, length, 15.0F, soundsObj, delaysObj);
    }

    public void playInCarAnnouncement(EntityTrainBase train, float length, float maxRadius, Object soundsObj, Object delaysObj) {
        if (train == null) {
            if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] playInCarAnnouncement aborted: train is null");
            return;
        }
        String[] sounds = parseStrings(soundsObj);
        int[] delaySeconds = parseDelays(delaysObj);
        if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] playInCarAnnouncement. trainId=" + train.getEntityId() + ", maxRadius=" + maxRadius + ", sounds=" + java.util.Arrays.toString(sounds) + ", delays=" + java.util.Arrays.toString(delaySeconds));
        int[] delayTicks = new int[delaySeconds.length];
        for (int i = 0; i < delaySeconds.length; i++) delayTicks[i] = delaySeconds[i] * 20;
        SoundQueueManager.getInstance().enqueueTrainSound(train.getEntityId(), length, maxRadius, sounds, delayTicks, (byte)0);
    }

    public void playExteriorSound(EntityTrainBase train, float length, Object soundsObj, Object delaysObj) {
        playExteriorSound(train, length, 30.0F, soundsObj, delaysObj);
    }

    public void playExteriorSound(EntityTrainBase train, float length, float maxRadius, Object soundsObj, Object delaysObj) {
        if (train == null) {
            if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] playExteriorSound aborted: train is null");
            return;
        }
        String[] sounds = parseStrings(soundsObj);
        int[] delaySeconds = parseDelays(delaysObj);
        if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug) System.out.println("[CrossTie-Debug] playExteriorSound. trainId=" + train.getEntityId() + ", maxRadius=" + maxRadius + ", sounds=" + java.util.Arrays.toString(sounds) + ", delays=" + java.util.Arrays.toString(delaySeconds));
        int[] delayTicks = new int[delaySeconds.length];
        for (int i = 0; i < delaySeconds.length; i++) delayTicks[i] = delaySeconds[i] * 20;
        SoundQueueManager.getInstance().enqueueTrainSound(train.getEntityId(), length, maxRadius, sounds, delayTicks, (byte)1);
    }

    public void playStationBroadcast(int dimension, Object coordsObj, float maxRadius, Object soundsObj, Object delaysObj) {
        double[][] coords = parseCoords(coordsObj);
        String[] sounds = parseStrings(soundsObj);
        int[] delaySeconds = parseDelays(delaysObj);
        int[] delayTicks = new int[delaySeconds.length];
        for (int i = 0; i < delaySeconds.length; i++) delayTicks[i] = delaySeconds[i] * 20;
        SoundQueueManager.getInstance().enqueueStationBroadcast(dimension, coords, maxRadius, sounds, delayTicks);
    }

    public void playStationLoop(String loopId, int dimension, Object coordsObj, float maxRadius, String sound, int intervalSeconds) {
        double[][] coords = parseCoords(coordsObj);
        SoundQueueManager.getInstance().startStationLoop(loopId, dimension, coords, maxRadius, sound, intervalSeconds * 20);
    }

    public void stopStationLoop(String loopId) {
        SoundQueueManager.getInstance().stopStationLoop(loopId);
    }

    // --- Nashorn JS Array Auto-Conversion Helpers ---
    private double[][] parseCoords(Object obj) {
        if (obj instanceof double[][]) return (double[][]) obj;
        if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            double[][] res = new double[arr.length][3];
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] instanceof Object[]) {
                    Object[] inner = (Object[]) arr[i];
                    if (inner.length > 0 && inner[0] instanceof Number) res[i][0] = ((Number) inner[0]).doubleValue();
                    if (inner.length > 1 && inner[1] instanceof Number) res[i][1] = ((Number) inner[1]).doubleValue();
                    if (inner.length > 2 && inner[2] instanceof Number) res[i][2] = ((Number) inner[2]).doubleValue();
                }
            }
            return res;
        }
        if (obj instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) obj;
            double[][] res = new double[list.size()][3];
            for (int i = 0; i < list.size(); i++) {
                Object innerObj = list.get(i);
                if (innerObj instanceof java.util.List) {
                    java.util.List<?> inner = (java.util.List<?>) innerObj;
                    if (inner.size() > 0 && inner.get(0) instanceof Number) res[i][0] = ((Number) inner.get(0)).doubleValue();
                    if (inner.size() > 1 && inner.get(1) instanceof Number) res[i][1] = ((Number) inner.get(1)).doubleValue();
                    if (inner.size() > 2 && inner.get(2) instanceof Number) res[i][2] = ((Number) inner.get(2)).doubleValue();
                }
            }
            return res;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            java.util.List<double[]> list = new java.util.ArrayList<double[]>();
            for (int i = 0; ; i++) {
                Object innerObj = map.get(String.valueOf(i));
                if (innerObj == null) break;
                if (innerObj instanceof Map) {
                    Map<?, ?> inner = (Map<?, ?>) innerObj;
                    double[] c = new double[3];
                    Object n0 = inner.get("0");
                    Object n1 = inner.get("1");
                    Object n2 = inner.get("2");
                    if (n0 instanceof Number) c[0] = ((Number) n0).doubleValue();
                    if (n1 instanceof Number) c[1] = ((Number) n1).doubleValue();
                    if (n2 instanceof Number) c[2] = ((Number) n2).doubleValue();
                    list.add(c);
                }
            }
            return list.toArray(new double[0][0]);
        }
        return new double[0][0];
    }

    private String[] parseStrings(Object obj) {
        if (obj instanceof String[]) return (String[]) obj;
        if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            String[] res = new String[arr.length];
            for (int i = 0; i < arr.length; i++) res[i] = String.valueOf(arr[i]);
            return res;
        }
        if (obj instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) obj;
            String[] res = new String[list.size()];
            for (int i = 0; i < list.size(); i++) res[i] = String.valueOf(list.get(i));
            return res;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            java.util.List<String> list = new java.util.ArrayList<String>();
            for (int i = 0; ; i++) {
                Object val = map.get(String.valueOf(i));
                if (val == null) break;
                list.add(String.valueOf(val));
            }
            return list.toArray(new String[0]);
        }
        return new String[0];
    }

    private int[] parseDelays(Object obj) {
        if (obj instanceof int[]) return (int[]) obj;
        if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            int[] res = new int[arr.length];
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] instanceof Number) res[i] = ((Number) arr[i]).intValue();
            }
            return res;
        }
        if (obj instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) obj;
            int[] res = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof Number) res[i] = ((Number) list.get(i)).intValue();
            }
            return res;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            java.util.List<Integer> list = new java.util.ArrayList<Integer>();
            for (int i = 0; ; i++) {
                Object val = map.get(String.valueOf(i));
                if (val == null) break;
                if (val instanceof Number) list.add(((Number) val).intValue());
                else list.add(0);
            }
            int[] res = new int[list.size()];
            for (int i = 0; i < list.size(); i++) res[i] = list.get(i);
            return res;
        }
        return new int[0];
    }
}
