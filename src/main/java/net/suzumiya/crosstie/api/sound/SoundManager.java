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
        SoundQueueManager.getInstance().enqueueTrainSound(train.getEntityId(), 20.0f, new String[]{soundName}, new int[]{0}, (byte)0);
    }
    
    // --- JavaScript Advanced APIs ---

    public void playInCarAnnouncement(EntityTrainBase train, float length, Object soundsObj, Object delaysObj) {
        String[] sounds = parseStrings(soundsObj);
        int[] delaySeconds = parseDelays(delaysObj);
        int[] delayTicks = new int[delaySeconds.length];
        for (int i = 0; i < delaySeconds.length; i++) delayTicks[i] = delaySeconds[i] * 20;
        SoundQueueManager.getInstance().enqueueTrainSound(train.getEntityId(), length, sounds, delayTicks, (byte)0);
    }

    public void playExteriorSound(EntityTrainBase train, float length, Object soundsObj, Object delaysObj) {
        String[] sounds = parseStrings(soundsObj);
        int[] delaySeconds = parseDelays(delaysObj);
        int[] delayTicks = new int[delaySeconds.length];
        for (int i = 0; i < delaySeconds.length; i++) delayTicks[i] = delaySeconds[i] * 20;
        SoundQueueManager.getInstance().enqueueTrainSound(train.getEntityId(), length, sounds, delayTicks, (byte)1);
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
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            double[][] res = new double[map.size()][3];
            for (int i = 0; i < map.size(); i++) {
                Object innerObj = map.get(String.valueOf(i));
                if (innerObj instanceof Map) {
                    Map<?, ?> inner = (Map<?, ?>) innerObj;
                    res[i][0] = ((Number) inner.get("0")).doubleValue();
                    res[i][1] = ((Number) inner.get("1")).doubleValue();
                    res[i][2] = ((Number) inner.get("2")).doubleValue();
                }
            }
            return res;
        }
        return new double[0][0];
    }

    private String[] parseStrings(Object obj) {
        if (obj instanceof String[]) return (String[]) obj;
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            String[] res = new String[map.size()];
            for (int i = 0; i < map.size(); i++) {
                res[i] = String.valueOf(map.get(String.valueOf(i)));
            }
            return res;
        }
        return new String[0];
    }

    private int[] parseDelays(Object obj) {
        if (obj instanceof int[]) return (int[]) obj;
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            int[] res = new int[map.size()];
            for (int i = 0; i < map.size(); i++) {
                res[i] = ((Number) map.get(String.valueOf(i))).intValue();
            }
            return res;
        }
        return new int[0];
    }
}
