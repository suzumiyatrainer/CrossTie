package net.suzumiya.crosstie.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.CrossTie;
import org.lwjgl.opengl.GL11;

/**
 * Discord などから送信された画像 (PNG / JPG / GIF 等) を、 Minecraft
 * 画面の中央に合わせて高画質・美麗にプレビュー表示する GUI スクリーン。
 */
@SideOnly(Side.CLIENT)
public class GuiImagePreview extends GuiScreen {

    private static final ExecutorService DOWNLOAD_WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrossTie-ImagePreview-Worker");
        t.setDaemon(true);
        return t;
    });

    // URL をキーとしたメモリキャッシュ (同一画像の再オープンを 0ms に高速化)
    private static final Map<String, CachedImageData> CACHE = new ConcurrentHashMap<>();

    private final String imageUrl;
    private final String fileName;

    private boolean loading = true;
    private String errorMessage = null;

    private List<FrameData> frames = new ArrayList<>();
    private int currentFrameIndex = 0;
    private long lastFrameTime = 0;

    private int originalWidth = 0;
    private int originalHeight = 0;

    // 表示モード (true: 画面フィット, false: 等倍 100%)
    private boolean fitToScreen = true;
    private int panX = 0;
    private int panY = 0;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private boolean isDragging = false;

    public GuiImagePreview(String imageUrl, String fileName) {
        this.imageUrl = imageUrl;
        this.fileName = (fileName != null && !fileName.isEmpty()) ? fileName : "image";
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        CachedImageData cached = CACHE.get(imageUrl);
        if (cached != null && !cached.frames.isEmpty()) {
            this.frames = cached.frames;
            this.originalWidth = cached.width;
            this.originalHeight = cached.height;
            this.loading = false;
            this.lastFrameTime = System.currentTimeMillis();
            return;
        }

        this.loading = true;
        this.errorMessage = null;

        DOWNLOAD_WORKER.submit(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "CrossTie-Minecraft-1.7.10");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new RuntimeException("HTTP " + code + ": " + conn.getResponseMessage());
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                }

                byte[] bytes = baos.toByteArray();
                if (bytes.length == 0) {
                    throw new RuntimeException("Empty response body");
                }

                List<BufferedImage> rawFrames = new ArrayList<>();
                List<Integer> delays = new ArrayList<>();

                // GIF アニメーションのフレーム抽出を試行
                boolean gifSuccess = false;
                try {
                    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                    if (readers.hasNext()) {
                        ImageReader reader = readers.next();
                        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                            reader.setInput(iis);
                            int numImages = reader.getNumImages(true);
                            if (numImages > 0) {
                                for (int i = 0; i < numImages; i++) {
                                    BufferedImage frame = reader.read(i);
                                    if (frame != null) {
                                        rawFrames.add(frame);
                                        delays.add(100); // デフォルト 100ms
                                    }
                                }
                                if (!rawFrames.isEmpty()) {
                                    gifSuccess = true;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                // 静止画フォールバック (PNG / JPG / WEBP / 通常デコード)
                if (!gifSuccess || rawFrames.isEmpty()) {
                    BufferedImage single = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (single == null) {
                        throw new RuntimeException("Unsupported image format");
                    }
                    rawFrames.add(single);
                    delays.add(1000);
                }

                int w = rawFrames.get(0).getWidth();
                int h = rawFrames.get(0).getHeight();

                // メインスレッドでのテクスチャ登録をスケジュール
                Minecraft.getMinecraft().func_152344_a(() -> {
                    try {
                        List<FrameData> loadedFrames = new ArrayList<>();
                        for (int i = 0; i < rawFrames.size(); i++) {
                            BufferedImage bi = rawFrames.get(i);
                            DynamicTexture dynTex = new DynamicTexture(bi);
                            ResourceLocation loc = Minecraft.getMinecraft().getTextureManager()
                                    .getDynamicTextureLocation("crosstie_img_" + System.nanoTime() + "_" + i, dynTex);
                            loadedFrames.add(new FrameData(loc, dynTex, delays.get(i)));
                        }

                        GuiImagePreview.this.frames = loadedFrames;
                        GuiImagePreview.this.originalWidth = w;
                        GuiImagePreview.this.originalHeight = h;
                        GuiImagePreview.this.loading = false;
                        GuiImagePreview.this.lastFrameTime = System.currentTimeMillis();

                        CACHE.put(imageUrl, new CachedImageData(loadedFrames, w, h));
                    } catch (Throwable t) {
                        GuiImagePreview.this.errorMessage = t.getMessage();
                        GuiImagePreview.this.loading = false;
                    }
                });

            } catch (Throwable t) {
                CrossTie.LOGGER.warn("[Discord] Failed to download image from {}: {}", imageUrl, t.getMessage());
                Minecraft.getMinecraft().func_152344_a(() -> {
                    GuiImagePreview.this.errorMessage = t.getMessage();
                    GuiImagePreview.this.loading = false;
                });
            }
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 高級感のある半透明ダークオーバーレイ (ガラスモーフィズム調背景)
        drawRect(0, 0, this.width, this.height, 0xDC0A0A0E);

        if (loading) {
            String msg = "§b画像を読み込み中...";
            int tw = this.fontRendererObj.getStringWidth(msg);
            this.fontRendererObj.drawStringWithShadow(msg, (this.width - tw) / 2, this.height / 2 - 10, 0xFFFFFF);
            String urlShort = (imageUrl.length() > 60) ? imageUrl.substring(0, 57) + "..." : imageUrl;
            this.fontRendererObj.drawStringWithShadow("§7" + urlShort,
                    (this.width - this.fontRendererObj.getStringWidth("§7" + urlShort)) / 2, this.height / 2 + 5,
                    0x888888);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        if (errorMessage != null || frames.isEmpty()) {
            String err = "§c画像の読み込みに失敗しました";
            int tw = this.fontRendererObj.getStringWidth(err);
            this.fontRendererObj.drawStringWithShadow(err, (this.width - tw) / 2, this.height / 2 - 20, 0xFF5555);
            String detail = "§7理由: " + (errorMessage != null ? errorMessage : "不明なエラー");
            this.fontRendererObj.drawStringWithShadow(detail,
                    (this.width - this.fontRendererObj.getStringWidth(detail)) / 2, this.height / 2, 0xAAAAAA);
            String hint = "§e[B] キーを押すとブラウザで直接画像を開けます";
            this.fontRendererObj.drawStringWithShadow(hint,
                    (this.width - this.fontRendererObj.getStringWidth(hint)) / 2, this.height / 2 + 20, 0xFFFF55);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        // アニメーション GIF のフレーム進行
        if (frames.size() > 1) {
            long now = System.currentTimeMillis();
            FrameData current = frames.get(currentFrameIndex);
            if (now - lastFrameTime >= current.delayMs) {
                currentFrameIndex = (currentFrameIndex + 1) % frames.size();
                lastFrameTime = now;
            }
        }

        FrameData frame = frames.get(currentFrameIndex);

        // 描画サイズと位置の計算 (アスペクト比維持で画面中央にフィット)
        int availW = this.width - 40;
        int availH = this.height - 60;
        if (availW < 10)
            availW = 10;
        if (availH < 10)
            availH = 10;

        float scale;
        if (fitToScreen) {
            scale = Math.min((float) availW / originalWidth, (float) availH / originalHeight);
        } else {
            scale = 1.0f; // 等倍 (100%)
        }

        int drawW = (int) (originalWidth * scale);
        int drawH = (int) (originalHeight * scale);
        int drawX = (this.width - drawW) / 2 + (fitToScreen ? 0 : panX);
        int drawY = (this.height - drawH) / 2 + (fitToScreen ? 0 : panY);

        // 画像の背後に薄いドロップシャドウ
        drawRect(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0x66000000);
        // 細い洗練された境界線
        drawRect(drawX - 1, drawY - 1, drawX + drawW + 1, drawY + drawH + 1, 0xFF3A3A4C);

        // 高画質テクスチャレンダリング
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        this.mc.getTextureManager().bindTexture(frame.location);

        // バイリニア補間 (高画質スムージング) を明示
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(drawX, drawY + drawH, this.zLevel, 0.0, 1.0);
        tessellator.addVertexWithUV(drawX + drawW, drawY + drawH, this.zLevel, 1.0, 1.0);
        tessellator.addVertexWithUV(drawX + drawW, drawY, this.zLevel, 1.0, 0.0);
        tessellator.addVertexWithUV(drawX, drawY, this.zLevel, 0.0, 0.0);
        tessellator.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();

        // 上部情報バー
        String title = "§f" + fileName + " §7(" + originalWidth + "×" + originalHeight
                + (frames.size() > 1 ? ", GIF " + frames.size() + " frames" : "") + ")";
        this.fontRendererObj.drawStringWithShadow(title, (this.width - this.fontRendererObj.getStringWidth(title)) / 2,
                10, 0xFFFFFF);

        // 下部操作ガイド
        String modeStr = fitToScreen ? "§a[画面フィット中 (Zで等倍)]" : "§e[等倍表示中 (ドラッグで移動/Zでフィット)]";
        String footer = "§7[ESC: 閉じる]   §b[B: ブラウザで開く]   " + modeStr;
        this.fontRendererObj.drawStringWithShadow(footer,
                (this.width - this.fontRendererObj.getStringWidth(footer)) / 2, this.height - 20, 0xCCCCCC);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            // 画像外の余白クリックで閉じる (画面フィット時)
            if (fitToScreen) {
                int availW = this.width - 40;
                int availH = this.height - 60;
                float scale = Math.min((float) availW / originalWidth, (float) availH / originalHeight);
                int drawW = (int) (originalWidth * scale);
                int drawH = (int) (originalHeight * scale);
                int drawX = (this.width - drawW) / 2;
                int drawY = (this.height - drawH) / 2;

                if (mouseX < drawX || mouseX > drawX + drawW || mouseY < drawY || mouseY > drawY + drawH) {
                    this.mc.displayGuiScreen(null);
                    return;
                }
            } else {
                isDragging = true;
                dragStartX = mouseX - panX;
                dragStartY = mouseY - panY;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!fitToScreen && isDragging && clickedMouseButton == 0) {
            panX = mouseX - dragStartX;
            panY = mouseY - dragStartY;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) {
            isDragging = false;
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // ESC (keyCode 1) で閉じる
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }

        // B キー (keyCode 48): ブラウザで開く
        if (keyCode == 48) {
            openInBrowser();
            return;
        }

        // Z キー (keyCode 44): フィット / 等倍モード切り替え
        if (keyCode == 44) {
            fitToScreen = !fitToScreen;
            panX = 0;
            panY = 0;
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private void openInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(imageUrl));
            } else {
                // OS 依存コマンドフォールバック
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + imageUrl);
            }
        } catch (Throwable t) {
            CrossTie.LOGGER.warn("[Discord] Failed to open URL in browser: {}", t.getMessage());
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // マルチプレイ中もゲームを中断させない
    }

    private static final class FrameData {
        public final ResourceLocation location;
        @SuppressWarnings("unused")
        public final DynamicTexture texture;
        public final int delayMs;

        public FrameData(ResourceLocation location, DynamicTexture texture, int delayMs) {
            this.location = location;
            this.texture = texture;
            this.delayMs = delayMs;
        }
    }

    private static final class CachedImageData {
        public final List<FrameData> frames;
        public final int width;
        public final int height;

        public CachedImageData(List<FrameData> frames, int width, int height) {
            this.frames = frames;
            this.width = width;
            this.height = height;
        }
    }
}
