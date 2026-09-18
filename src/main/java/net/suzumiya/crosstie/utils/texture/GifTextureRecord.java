package net.suzumiya.crosstie.utils.texture;

import net.minecraft.util.ResourceLocation;

public class GifTextureRecord {
    
    private final ResourceLocation location;
    private final String path;
    private int[] textureIds;
    private int[] delayTimes;
    private int totalDuration;

    public GifTextureRecord(ResourceLocation location) {
        this.location = location;
        this.path = location.getResourcePath();
        GifDecoderUtils.loadGif(this);
    }

    public GifTextureRecord(String path) {
        this(path.contains(":") ? new ResourceLocation(path) : new ResourceLocation("minecraft", path));
    }

    public ResourceLocation getLocation() {
        return location;
    }

    public String getPath() {
        return path;
    }

    public void setFrames(int[] textureIds, int[] delayTimes, int totalDuration) {
        this.textureIds = textureIds;
        this.delayTimes = delayTimes;
        this.totalDuration = totalDuration;
    }

    public int getCurrentTextureId(long currentTimeMillis) {
        if (textureIds == null || textureIds.length == 0) return 0;
        if (textureIds.length == 1 || totalDuration <= 0) return textureIds[0];

        int currentCycleTime = (int) (currentTimeMillis % totalDuration);
        int timeAccumulator = 0;

        for (int i = 0; i < delayTimes.length; i++) {
            timeAccumulator += delayTimes[i];
            if (currentCycleTime < timeAccumulator) {
                return textureIds[i];
            }
        }
        
        return textureIds[textureIds.length - 1]; // fallback
    }

    public void deleteTextures() {
        if (textureIds != null) {
            for (int id : textureIds) {
                if (id != 0) {
                    org.lwjgl.opengl.GL11.glDeleteTextures(id);
                }
            }
            textureIds = null;
        }
    }
}
