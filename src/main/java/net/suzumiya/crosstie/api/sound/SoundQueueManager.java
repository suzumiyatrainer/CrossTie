package net.suzumiya.crosstie.api.sound;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.suzumiya.crosstie.network.CrossTiePacketHandler;
import net.suzumiya.crosstie.network.MessagePlayStationSound;
import net.suzumiya.crosstie.network.MessagePlayTrainSound;
import net.suzumiya.crosstie.network.MessageStopSound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SoundQueueManager {

    private static final SoundQueueManager INSTANCE = new SoundQueueManager();

    private final List<QueueTask> queue = new ArrayList<>();
    private final Map<String, LoopTask> loops = new ConcurrentHashMap<>();

    private SoundQueueManager() {
        FMLCommonHandler.instance().bus().register(this);
    }

    public static SoundQueueManager getInstance() {
        return INSTANCE;
    }

    // --- Train Task ---
    public void enqueueTrainSound(int entityId, float length, String[] sounds, int[] delayTicks, byte type) {
        for (int i = 0; i < sounds.length; i++) {
            if (i < delayTicks.length) {
                queue.add(new TrainQueueTask(entityId, length, sounds[i], delayTicks[i], type));
            }
        }
    }

    // --- Station Task ---
    public void enqueueStationBroadcast(int dimension, double[][] coords, float maxRadius, String[] sounds, int[] delayTicks) {
        for (int i = 0; i < sounds.length; i++) {
            if (i < delayTicks.length) {
                queue.add(new StationQueueTask(dimension, coords, maxRadius, sounds[i], delayTicks[i]));
            }
        }
    }

    public void startStationLoop(String loopId, int dimension, double[][] coords, float maxRadius, String sound, int intervalTicks) {
        if (intervalTicks == 0) {
            // Gapless loop
            CrossTiePacketHandler.INSTANCE.sendToDimension(
                new MessagePlayStationSound(coords, maxRadius, sound, loopId, true), dimension
            );
        } else {
            // Interval loop
            loops.put(loopId, new LoopTask(dimension, coords, maxRadius, sound, intervalTicks));
            // Play first time immediately
            CrossTiePacketHandler.INSTANCE.sendToDimension(
                new MessagePlayStationSound(coords, maxRadius, sound, loopId, false), dimension
            );
        }
    }

    public void stopStationLoop(String loopId) {
        loops.remove(loopId);
        CrossTiePacketHandler.INSTANCE.sendToAll(new MessageStopSound(loopId));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 1. Process Queue
        Iterator<QueueTask> it = queue.iterator();
        while (it.hasNext()) {
            QueueTask task = it.next();
            if (task.tick()) {
                task.execute();
                it.remove();
            }
        }

        // 2. Process Loops
        for (LoopTask loop : loops.values()) {
            if (loop.tick()) {
                loop.execute();
            }
        }
    }

    private static abstract class QueueTask {
        int ticksRemaining;
        QueueTask(int delay) { this.ticksRemaining = delay; }
        boolean tick() {
            if (ticksRemaining > 0) ticksRemaining--;
            return ticksRemaining <= 0;
        }
        abstract void execute();
    }

    private static class TrainQueueTask extends QueueTask {
        int entityId;
        float length;
        String soundName;
        byte type;

        TrainQueueTask(int entityId, float length, String soundName, int delay, byte type) {
            super(delay);
            this.entityId = entityId;
            this.length = length;
            this.soundName = soundName;
            this.type = type;
        }

        @Override
        void execute() {
            CrossTiePacketHandler.INSTANCE.sendToAll(new MessagePlayTrainSound(entityId, length, soundName, type));
        }
    }

    private static class StationQueueTask extends QueueTask {
        int dimension;
        double[][] coords;
        float maxRadius;
        String soundName;

        StationQueueTask(int dimension, double[][] coords, float maxRadius, String soundName, int delay) {
            super(delay);
            this.dimension = dimension;
            this.coords = coords;
            this.maxRadius = maxRadius;
            this.soundName = soundName;
        }

        @Override
        void execute() {
            CrossTiePacketHandler.INSTANCE.sendToDimension(
                new MessagePlayStationSound(coords, maxRadius, soundName, "", false), dimension
            );
        }
    }

    private static class LoopTask {
        int dimension;
        double[][] coords;
        float maxRadius;
        String soundName;
        int intervalTicks;
        int currentTick;

        LoopTask(int dimension, double[][] coords, float maxRadius, String soundName, int intervalTicks) {
            this.dimension = dimension;
            this.coords = coords;
            this.maxRadius = maxRadius;
            this.soundName = soundName;
            this.intervalTicks = intervalTicks;
            this.currentTick = intervalTicks;
        }

        boolean tick() {
            if (currentTick > 0) currentTick--;
            if (currentTick <= 0) {
                currentTick = intervalTicks;
                return true;
            }
            return false;
        }

        void execute() {
            CrossTiePacketHandler.INSTANCE.sendToDimension(
                new MessagePlayStationSound(coords, maxRadius, soundName, "", false), dimension
            );
        }
    }
}
