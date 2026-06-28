package net.suzumiya.crosstie.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

public class GuiReloadingWaitScreen extends GuiScreen {

    private final Thread loadingThread;
    private GuiButton okButton;
    private boolean isFinished = false;

    public volatile boolean hasError = false;

    private int totalModels = -1;
    private int loadedModels = 0;
    private int updateTimer = 0;

    public GuiReloadingWaitScreen(Thread loadingThread) {
        this.loadingThread = loadingThread;
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    public void initGui() {
        this.buttonList.clear();
        this.okButton = new GuiButton(0, this.width / 2 - 100, this.height / 2 + 50, 200, 20, I18n.format("gui.ok"));
        this.okButton.enabled = false;
        this.okButton.visible = false;
        this.buttonList.add(this.okButton);

        new Thread(() -> {
            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Model.*\\.json");
                java.util.List<java.io.File> fileList = jp.ngt.ngtlib.io.NGTFileLoader.findFile(file -> pattern.matcher(file.getName()).matches());
                this.totalModels = fileList.size();
            } catch (Exception e) {
                this.totalModels = 0;
            }
        }).start();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!this.isFinished && (this.loadingThread == null || !this.loadingThread.isAlive())) {
            this.isFinished = true;
            this.okButton.enabled = true;
            this.okButton.visible = true;
            if (!this.hasError) {
                try {
                    net.minecraft.util.ResourceLocation soundLoc = new net.minecraft.util.ResourceLocation("crosstie", "sounds/rtmreloaded.wav");
                    java.io.InputStream is = this.mc.getResourceManager().getResource(soundLoc).getInputStream();
                    javax.sound.sampled.AudioInputStream audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(is));
                    javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                    clip.open(audioIn);
                    
                    if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                        javax.sound.sampled.FloatControl gainControl = (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                        float dB = (float) (20.0 * Math.log10(0.9));
                        gainControl.setValue(dB);
                    }
                    
                    clip.start();
                } catch (Exception e) {
                    net.suzumiya.crosstie.CrossTie.LOGGER.error("Failed to play completion sound using standard Java audio, falling back to Minecraft sound engine", e);
                    try {
                        this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(new net.minecraft.util.ResourceLocation("crosstie", "rtmreloaded"), 1.0F));
                    } catch (Exception fallbackEx) {}
                }
            }
        }

        if (!this.isFinished) {
            this.updateTimer++;
            if (this.updateTimer >= 100) { // 5秒ごと (20 TPS * 5 = 100 ticks)
                this.updateTimer = 0;
                try {
                    java.lang.reflect.Field mapField = jp.ngt.rtm.modelpack.ModelPackManager.class.getDeclaredField("allModelSetMap");
                    mapField.setAccessible(true);
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) mapField.get(jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE);
                    int loaded = 0;
                    for (Object innerMapObj : map.values()) {
                        if (innerMapObj instanceof java.util.Map) {
                            loaded += ((java.util.Map<?, ?>) innerMapObj).size();
                        }
                    }
                    this.loadedModels = loaded;
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        GL11.glPushMatrix();
        GL11.glScalef(2.0F, 2.0F, 2.0F);

        String message;
        int color = 0xFFFFFF;
        if (this.hasError) {
            message = "予期せぬエラーが発生しました、ログをご確認ください。";
            color = 0xFF5555; // Red
        } else if (this.isFinished) {
            message = I18n.format("crosstie.gui.reloadPacks.done");
        } else {
            message = I18n.format("crosstie.gui.reloadPacks.wait");
        }

        // Use drawString with centering manually if string is too long
        int stringWidth = this.fontRendererObj.getStringWidth(message);
        float scale = 1.0F;
        if (stringWidth > this.width / 2 - 10) {
            scale = (float) (this.width / 2 - 10) / stringWidth;
        }

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        this.drawCenteredString(this.fontRendererObj, message, (int) ((this.width / 4) / scale),
                (int) ((this.height / 4 - 30) / scale), color);
        GL11.glPopMatrix();

        GL11.glPopMatrix();

        String cacheMsgKey = net.suzumiya.crosstie.gui.RTMReloadPacksLogic.currentCacheMessage;
        if (cacheMsgKey != null) {
            String cacheMsg = net.minecraft.client.resources.I18n.format(cacheMsgKey);
            this.drawCenteredString(this.fontRendererObj, cacheMsg, this.width / 2, this.height / 2 + 10, 0xAAAAAA);
        } else if (!this.isFinished && this.totalModels > 0) {
            int barWidth = 200;
            int barHeight = 10;
            int x = (this.width - barWidth) / 2;
            int y = this.height / 2 + 10;
            
            drawRect(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
            drawRect(x, y, x + barWidth, y + barHeight, 0xFF222222);
            
            float progress = Math.min(1.0f, (float) this.loadedModels / this.totalModels);
            int filledWidth = (int) (barWidth * progress);
            drawRect(x, y, x + filledWidth, y + barHeight, 0xFFBFE4EB);
            
            String progressText = String.format("%.1f%%", progress * 100.0f);
            this.drawCenteredString(this.fontRendererObj, progressText, this.width / 2, y + barHeight + 5, 0xFFFFFF);
        } else if (!this.isFinished && this.totalModels == -1) {
            String calculatingText = "Calculating total models...";
            this.drawCenteredString(this.fontRendererObj, calculatingText, this.width / 2, this.height / 2 + 10, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && this.isFinished) {
            if (!this.hasError) {
                reloadRTMTexturesFast();
            }
            this.mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
            System.gc();
        }
    }

    @SuppressWarnings("unchecked")
    private void reloadRTMTexturesFast() {
        try {
            net.minecraft.client.renderer.texture.TextureManager tm = this.mc.getTextureManager();
            java.lang.reflect.Field mapField = null;
            for (java.lang.reflect.Field f : net.minecraft.client.renderer.texture.TextureManager.class.getDeclaredFields()) {
                if (f.getType() == java.util.Map.class) {
                    mapField = f;
                    break;
                }
            }
            if (mapField != null) {
                mapField.setAccessible(true);
                java.util.Map<net.minecraft.util.ResourceLocation, net.minecraft.client.renderer.texture.ITextureObject> map = 
                    (java.util.Map<net.minecraft.util.ResourceLocation, net.minecraft.client.renderer.texture.ITextureObject>) mapField.get(tm);
                
                int count = 0;
                java.util.List<net.minecraft.util.ResourceLocation> rtmTextures = new java.util.ArrayList<net.minecraft.util.ResourceLocation>();
                for (java.util.Map.Entry<net.minecraft.util.ResourceLocation, net.minecraft.client.renderer.texture.ITextureObject> entry : map.entrySet()) {
                    net.minecraft.util.ResourceLocation res = entry.getKey();
                    net.minecraft.client.renderer.texture.ITextureObject tex = entry.getValue();
                    
                    if (res != null && tex != null && !(tex instanceof net.minecraft.client.renderer.texture.TextureMap)) {
                        String domain = res.getResourceDomain();
                        String path = res.getResourcePath();
                        
                        boolean isRTM = false;
                        if (!domain.equals("minecraft") && !domain.equals("realms") && !domain.equals("forge")) {
                            isRTM = true;
                        } else if (domain.equals("minecraft") && (path.startsWith("textures/train/") || path.startsWith("models/") || path.contains("rtm"))) {
                            isRTM = true;
                        }

                        if (isRTM) {
                            rtmTextures.add(res);
                        }
                    }
                }

                for (net.minecraft.util.ResourceLocation res : rtmTextures) {
                    try {
                        net.minecraft.client.renderer.texture.ITextureObject oldTex = map.get(res);
                        int glId = -1;
                        if (oldTex != null) {
                            glId = oldTex.getGlTextureId();
                        }
                        
                        java.io.InputStream is = null;
                        try {
                            is = jp.ngt.ngtlib.io.NGTFileLoader.getInputStream(res);
                        } catch (Exception e) {
                            try {
                                net.minecraft.client.resources.IResource iresource = this.mc.getResourceManager().getResource(res);
                                is = iresource.getInputStream();
                            } catch (Exception ex) {}
                        }

                        if (is != null) {
                            java.awt.image.BufferedImage bufferedimage = javax.imageio.ImageIO.read(is);
                            is.close();
                            if (bufferedimage != null) {
                                if (glId == -1) {
                                    net.minecraft.client.renderer.texture.SimpleTexture simpleTex = new net.minecraft.client.renderer.texture.SimpleTexture(res);
                                    glId = simpleTex.getGlTextureId();
                                    map.put(res, simpleTex);
                                }
                                net.minecraft.client.renderer.texture.TextureUtil.uploadTextureImageAllocate(glId, bufferedimage, false, false);
                                count++;
                            }
                        }
                    } catch (Exception e) {}
                }
                net.suzumiya.crosstie.CrossTie.LOGGER.info("Fast reloaded " + count + " RTM textures.");
            }
        } catch (Exception e) {
            net.suzumiya.crosstie.CrossTie.LOGGER.error("Failed to fast-reload RTM textures", e);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Disable ESC to prevent closing while loading
        if (this.isFinished && keyCode == 1) {
            this.mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
        }
    }
}
