package net.suzumiya.crosstie.client.sound;

import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StationBroadcastSound extends PositionedSound implements ITickableSound {

    // クライアント側でループIDに紐づくサウンドインスタンスを保持し、強制停止パケット受信時に止める
    public static final Map<String, StationBroadcastSound> ACTIVE_LOOPS = new ConcurrentHashMap<>();

    private final double[] coords;
    private final float maxRadius;
    public final String loopId;
    private boolean donePlaying = false;

    public StationBroadcastSound(double[] coords, float maxRadius, String soundName, String loopId, boolean isLoop) {
        super(new ResourceLocation(soundName));
        this.coords = coords;
        this.maxRadius = maxRadius;
        this.loopId = loopId;
        this.repeat = isLoop;
        this.volume = 0.0F; // 初期値
        this.field_147663_c = 1.0F; // pitch

        if (loopId != null && !loopId.isEmpty() && isLoop) {
            StationBroadcastSound old = ACTIVE_LOOPS.put(loopId, this);
            if (old != null) {
                old.stop();
            }
        }
        update();
    }

    public void stop() {
        this.donePlaying = true;
        if (loopId != null && !loopId.isEmpty()) {
            ACTIVE_LOOPS.remove(loopId);
        }
    }

    @Override
    public boolean isDonePlaying() {
        return donePlaying;
    }

    @Override
    public void update() {
        if (donePlaying) return;

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        // 複数のスピーカー座標の中で、プレイヤーに一番近い座標を見つける
        double minDistSq = Double.MAX_VALUE;
        double closestX = coords[0];
        double closestY = coords[1];
        double closestZ = coords[2];

        for (int i = 0; i < coords.length; i += 3) {
            double cx = coords[i];
            double cy = coords[i];
            double cz = coords[i+2];
            double distSq = (player.posX - cx) * (player.posX - cx) + 
                            (player.posY - cy) * (player.posY - cy) + 
                            (player.posZ - cz) * (player.posZ - cz);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                closestX = cx;
                closestY = cy;
                closestZ = cz;
            }
        }

        // 発生源を一番近いスピーカーに設定
        this.xPosF = (float) closestX;
        this.yPosF = (float) closestY;
        this.zPosF = (float) closestZ;

        double dist = Math.sqrt(minDistSq);

        if (dist >= maxRadius) {
            this.volume = 0.0F;
        } else {
            // maxRadiusの端で0になる線形減衰
            this.volume = 1.0F * (float) (1.0 - (dist / maxRadius));
        }
    }
}
