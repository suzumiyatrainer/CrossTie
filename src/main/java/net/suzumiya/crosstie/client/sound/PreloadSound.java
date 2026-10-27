package net.suzumiya.crosstie.client.sound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.util.ResourceLocation;

/**
 * サウンドファイルを OpenAL / Paulscode SoundSystem に事前キャッシュ（ウォームアップ）させるための
 * 完全無音・即時解放チケット音。
 *
 * <p>
 * ボリュームを可聴域外（0.00001F）にして 2 tick のみ再生キューを通すことで、
 * 本番再生時のディスク I/O や Ogg デコード遅延を排除し、0 ミリ秒の即時再生を実現する。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class PreloadSound implements ITickableSound {

    private final ResourceLocation soundLocation;
    private boolean isDone = false;
    private int ticksElapsed = 0;

    public PreloadSound(ResourceLocation soundLocation) {
        this.soundLocation = soundLocation;
    }

    @Override
    public ResourceLocation getPositionedSoundLocation() {
        return this.soundLocation;
    }

    @Override
    public boolean canRepeat() {
        return false;
    }

    @Override
    public int getRepeatDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return 0.00001F; // 完全無音（プレイヤーには一切聞こえない）
    }

    @Override
    public float getPitch() {
        return 1.0F;
    }

    @Override
    public float getXPosF() {
        return 0.0F;
    }

    @Override
    public float getYPosF() {
        return 0.0F;
    }

    @Override
    public float getZPosF() {
        return 0.0F;
    }

    @Override
    public AttenuationType getAttenuationType() {
        return AttenuationType.NONE;
    }

    @Override
    public boolean isDonePlaying() {
        return this.isDone;
    }

    @Override
    public void update() {
        // 2 tick 経過後に終了フラグを立てて SoundSystem の再生チャンネルを直ちに解放する
        if (++this.ticksElapsed >= 2) {
            this.isDone = true;
        }
    }
}
