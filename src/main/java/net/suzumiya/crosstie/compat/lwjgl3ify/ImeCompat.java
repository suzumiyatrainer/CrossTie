package net.suzumiya.crosstie.compat.lwjgl3ify;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.suzumiya.crosstie.CrossTie;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * lwjgl3ify (SDL3) 環境における日本語IMEの汎用補正。Mod 個別の対応ではなく全 Mod 共通で効く。
 *
 * <ol>
 * <li>{@link SdlImeBridge} 経由で
 * {@code SDL_HINT_IME_IMPLEMENTED_UI = "composition,candidates"} を設定し、Windows
 * IMM32 の不自然な白枠コンポジション小窓(MS ゴシックの「あ」)を抑止する。</li>
 * <li>未確定文字列({@code SDL_EVENT_TEXT_EDITING})および変換候補({@code SDL_EVENT_TEXT_EDITING_CANDIDATES})を
 * Minecraft 自身の {@link FontRenderer} (MinFo 等のフォント変更 Mod を含む)でテキストバー上に直接描画し、
 * フォントとデザインの完全な整合性を保つ。</li>
 * <li>{@code SDL_SetTextInputArea} でカーソル位置をリアルタイム通知し、OS 側の補助候補窓が出る場合も
 * テキストバーの真上/真下に正確に配置させる。</li>
 * <li>{@code SDL_EVENT_KEY_DOWN} による生文字入力と {@code TextFieldHandler.textBuffer}
 * の残留による 2文字・複数回入力バグを防止する。</li>
 * </ol>
 *
 * <p>
 * 無効化: JVM 引数 {@code -Dcrosstie.disableImeFix=true}
 */
public final class ImeCompat {

    private static final boolean DISABLED = Boolean.getBoolean("crosstie.disableImeFix");
    private static final int CANDIDATE_PAGE_SIZE = 9;

    private static Method execMethod;
    private static StringBuilder lwjgl3ifyTextBuffer;
    private static boolean available;
    private static boolean errorLogged;

    static {
        try {
            Class.forName("org.lwjglx.opengl.Display");
            Class<?> execCls = Class.forName("me.eigenraven.lwjgl3ify.client.MainThreadExec");
            execMethod = execCls.getMethod("runOnMainThread", Runnable.class);

            try {
                Class<?> tfhCls = Class.forName("me.eigenraven.lwjgl3ify.client.TextFieldHandler");
                Field tbField = tfhCls.getDeclaredField("textBuffer");
                tbField.setAccessible(true);
                Object tb = tbField.get(null);
                if (tb instanceof StringBuilder) {
                    lwjgl3ifyTextBuffer = (StringBuilder) tb;
                }
            } catch (Throwable ignored) {
            }

            available = !DISABLED;
            if (available) {
                SdlImeBridge.registerEarlyWindowHook();
            }
        } catch (Throwable t) {
            available = false;
        }
    }

    public static void registerEarlyHooks() {
        if (DISABLED) {
            return;
        }
        try {
            Class.forName("org.lwjglx.opengl.Display");
            SdlImeBridge.registerEarlyWindowHook();
        } catch (Throwable ignored) {
        }
    }

    // ---- SDL TextInput 状態と TextFieldHandler.textBuffer 制御 ---- //

    private static volatile boolean sdlTextInputActive = false;

    public static boolean isSdlTextInputActive() {
        return available && sdlTextInputActive;
    }

    /**
     * {@code keyCode == KEY_NONE (0)} の確定文字イベント処理直前に呼び出し、 {@code MixinGuiTextField}
     * が参照する {@code TextFieldHandler.textBuffer} を
     * 今回の1文字だけに揃える(過去フレームの未消費文字蓄積による2文字・複数文字入力を防ぐ)。
     */
    public static void prepareLwjgl3ifyTextBufferForChar(char c) {
        if (lwjgl3ifyTextBuffer != null) {
            lwjgl3ifyTextBuffer.setLength(0);
            if (c >= 32 && c != 127) {
                lwjgl3ifyTextBuffer.append(c);
            }
        }
    }

    /**
     * {@code TextFieldHandler.textBuffer} を空にし、不要な文字の持ち越しを防ぐ。
     */
    public static void clearLwjgl3ifyTextBuffer() {
        if (lwjgl3ifyTextBuffer != null && lwjgl3ifyTextBuffer.length() > 0) {
            lwjgl3ifyTextBuffer.setLength(0);
        }
    }

    /**
     * 全 Mod 共通の二重入力防止フィルター。
     * <p>
     * SDL TextInput がアクティブな場合、物理キー押下 ({@code SDL_EVENT_KEY_DOWN}) の印字可能文字は
     * 直後に必ず {@code SDL_EVENT_TEXT_INPUT} ({@code keyCode == KEY_NONE}) として同一文字が送信されるため、
     * 物理キー押下側の文字入力を {@link Keyboard#CHAR_NONE} ('\0') にマスクして二重入力を防ぐ。
     * ショートカットキー (Ctrl+C, Ctrl+V 等) や移動・削除キー (Backspace, Delete, Home, End, 矢印) はマスクしない。
     *
     * @param typedChar 入力文字
     * @param keyCode キーコード
     * @return フィルタリング後の文字 (マスク時は '\0')
     */
    public static char filterKeyTypedChar(char typedChar, int keyCode) {
        if (!available || !isSdlTextInputActive()) {
            return typedChar;
        }
        if (keyCode == Keyboard.KEY_NONE) {
            // SDL_EVENT_TEXT_INPUT による正規の文字入力イベント (半角英数・記号・全角IME確定文字)
            prepareLwjgl3ifyTextBufferForChar(typedChar);
            return typedChar;
        }

        // 物理キー押下 (SDL_EVENT_KEY_DOWN) によるイベント
        clearLwjgl3ifyTextBuffer();

        if (typedChar >= 32 && typedChar != 127 && !GuiScreen.isCtrlKeyDown()
                && keyCode != Keyboard.KEY_BACK
                && keyCode != Keyboard.KEY_HOME
                && keyCode != Keyboard.KEY_LEFT
                && keyCode != Keyboard.KEY_RIGHT
                && keyCode != Keyboard.KEY_END
                && keyCode != Keyboard.KEY_DELETE) {
            // 直後に必ず keyCode == KEY_NONE の TEXT_INPUT イベントで同一文字が送られてくるため、
            // 物理キー側の文字入力をマスクして全 Mod 共通で二重入力を防止する
            return Keyboard.CHAR_NONE;
        }

        return typedChar;
    }

    private static Method keyTypedMethod = null;
    private static boolean keyTypedMethodResolved = false;

    /**
     * {@link GuiScreen#keyTyped(char, int)} (または難読化名 {@code func_73869_a}) を
     * リフレクションを用いて安全にディスパッチする。
     */
    public static void dispatchKeyTyped(GuiScreen screen, char typedChar, int keyCode) {
        if (screen == null) {
            return;
        }
        if (!keyTypedMethodResolved) {
            resolveKeyTypedMethod();
        }
        if (keyTypedMethod != null) {
            try {
                keyTypedMethod.invoke(screen, typedChar, keyCode);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static synchronized void resolveKeyTypedMethod() {
        if (keyTypedMethodResolved) {
            return;
        }
        keyTypedMethodResolved = true;
        for (String name : new String[] { "keyTyped", "func_73869_a" }) {
            try {
                Method m = GuiScreen.class.getDeclaredMethod(name, char.class, int.class);
                m.setAccessible(true);
                keyTypedMethod = m;
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- 未確定文字列(Composition) & 変換候補(Candidates) ---- //

    private static volatile String compositionText = "";
    private static volatile int compositionStart = 0;
    private static volatile int compositionLength = 0;

    private static volatile String[] candidateList = null;
    private static volatile int selectedCandidate = -1;
    @SuppressWarnings("unused")
    private static volatile boolean candidatesHorizontal = false;

    public static void onTextEditing(String text, int start, int length) {
        compositionText = text != null ? text : "";
        compositionStart = Math.max(0, start);
        compositionLength = Math.max(0, length);
        if (compositionText.isEmpty()) {
            candidateList = null;
            selectedCandidate = -1;
        }
        lastX = Integer.MIN_VALUE;
    }

    public static void onTextEditingCandidates(String[] candidates, int selected, boolean horizontal) {
        if (candidates == null || candidates.length == 0) {
            candidateList = null;
            selectedCandidate = -1;
        } else {
            candidateList = candidates;
            selectedCandidate = selected;
            candidatesHorizontal = horizontal;
        }
    }

    public static void onTextCommitted() {
        compositionText = "";
        compositionStart = 0;
        compositionLength = 0;
        candidateList = null;
        selectedCandidate = -1;
        lastX = Integer.MIN_VALUE;
    }

    public static boolean hasActiveComposition() {
        String comp = compositionText;
        return comp != null && !comp.isEmpty();
    }

    // ---- アクティブ GuiTextField 追跡 & クリック位置(フォールバック用) ---- //

    private static volatile WeakReference<GuiTextField> activeTextFieldRef = new WeakReference<>(null);
    private static volatile int lastClickX = -1;
    private static volatile int lastClickY = -1;

    /**
     * {@code GuiTextField} または {@code GuiTextFieldCustom} が描画・フォーカスされた際に呼ばれる。
     */
    public static void onTextFieldActive(GuiTextField field) {
        if (!available || field == null) {
            return;
        }
        if (field.isFocused() && field.getVisible()) {
            GuiTextField prev = activeTextFieldRef.get();
            if (prev != field) {
                activeTextFieldRef = new WeakReference<>(field);
                lastX = Integer.MIN_VALUE;
            }
        }
    }

    /** マウスボタン押下時に呼ぶ。フレームバッファ px 座標(上原点)で保持。 */
    public static void onClick(int mouseX, int mouseYBottomOrigin) {
        if (!available) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            lastClickX = mouseX;
            lastClickY = mc.displayHeight - mouseYBottomOrigin;
            lastX = Integer.MIN_VALUE;
        }
    }

    // ---- 毎tick / 毎フレーム処理 ---- //

    private static boolean startedByUs = false;
    private static int lastX = Integer.MIN_VALUE, lastY, lastW, lastH, lastCursor;
    private static GuiScreen lastScreen;
    private static long lastOverlayRenderFrame = -1L;
    private static long frameCounter = 0L;
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new HashMap<>();
    private static final Map<Class<?>, Field> LINE_SCROLL_FIELD_CACHE = new HashMap<>();
    private static final Map<Class<?>, Field> FONT_RENDERER_FIELD_CACHE = new HashMap<>();

    public static void onScreenClosed() {
        if (!available) {
            return;
        }
        onTextCommitted();
        clearLwjgl3ifyTextBuffer();
        activeTextFieldRef = new WeakReference<>(null);
        lastX = Integer.MIN_VALUE;
        lastScreen = null;
        final long window = SdlImeBridge.getWindow();
        if (window != 0L) {
            startedByUs = false;
            sdlTextInputActive = false;
            runOnMain(() -> SdlImeBridge.stopTextInput(window));
        }
    }

    public static void beginFrame() {
        frameCounter++;
    }

    public static void tick() {
        if (!available) {
            return;
        }
        SdlImeBridge.ensureInitialized();
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        final GuiScreen screen = mc.currentScreen;
        final long window = SdlImeBridge.getWindow();
        if (window == 0L) {
            return;
        }

        if (screen == null) {
            if (startedByUs || sdlTextInputActive) {
                startedByUs = false;
                sdlTextInputActive = false;
                onTextCommitted();
                clearLwjgl3ifyTextBuffer();
                runOnMain(() -> SdlImeBridge.stopTextInput(window));
            }
            activeTextFieldRef = new WeakReference<>(null);
            lastX = Integer.MIN_VALUE;
            lastScreen = null;
            return;
        }
        if (screen != lastScreen) {
            lastScreen = screen;
            activeTextFieldRef = new WeakReference<>(null);
            onTextCommitted();
            clearLwjgl3ifyTextBuffer();
            lastClickX = -1;
            lastClickY = -1;
            lastX = Integer.MIN_VALUE; // 画面遷移時に再通知
        }

        final GuiTextField focused = resolveFocusedTextField(screen);
        final boolean forceStart = !(screen instanceof GuiContainer) || focused != null;
        final boolean shouldStopOurInput = (screen instanceof GuiContainer) && focused == null && startedByUs;
        final int[] rect = computeAreaFramebufferPx(mc, screen, focused); // x,y,w,h,cursor (px)

        runOnMain(() -> {
            try {
                if (shouldStopOurInput) {
                    SdlImeBridge.stopTextInput(window);
                    startedByUs = false;
                    sdlTextInputActive = false;
                    onTextCommitted();
                    lastX = Integer.MIN_VALUE;
                    return;
                }
                boolean active = SdlImeBridge.isTextInputActive(window);
                if (!active && forceStart) {
                    SdlImeBridge.startTextInput(window);
                    startedByUs = true;
                    active = true;
                    lastX = Integer.MIN_VALUE;
                }
                sdlTextInputActive = active;
                if (!active) {
                    lastX = Integer.MIN_VALUE;
                    return;
                }
                if (rect[0] == lastX && rect[1] == lastY && rect[2] == lastW && rect[3] == lastH
                        && rect[4] == lastCursor) {
                    return;
                }
                lastX = rect[0];
                lastY = rect[1];
                lastW = rect[2];
                lastH = rect[3];
                lastCursor = rect[4];
                SdlImeBridge.updateTextInputArea(window, rect[0], rect[1], rect[2], rect[3], rect[4]);
            } catch (Throwable t) {
                if (!errorLogged) {
                    errorLogged = true;
                    CrossTie.LOGGER.warn("[CrossTie] Failed to update SDL IME text input area", t);
                }
            }
        });
    }

    /**
     * Minecraft の {@link FontRenderer} (MinFo 等を含む) を用いて、
     * アクティブなテキストバー上に未確定文字列(プリエディット)と変換候補ウィンドウを描画する。
     */
    public static void renderOverlay(GuiScreen screen) {
        if (!available || screen == null) {
            return;
        }
        if (lastOverlayRenderFrame == frameCounter) {
            return;
        }
        String comp = compositionText;
        String[] cands = candidateList;
        boolean hasComp = comp != null && !comp.isEmpty();
        boolean hasCands = cands != null && cands.length > 0;
        if (!hasComp && !hasCands) {
            return;
        }
        lastOverlayRenderFrame = frameCounter;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = Math.max(1, sr.getScaleFactor());
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        GuiTextField focused = resolveFocusedTextField(screen);
        FontRenderer fr = focused != null ? getFontRenderer(mc, focused) : mc.fontRenderer;
        if (fr == null) {
            fr = mc.fontRenderer;
        }

        int cursorGuiX;
        int cursorGuiY;
        if (focused != null) {
            boolean bg = focused.getEnableBackgroundDrawing();
            int padX = bg ? 4 : 0;
            int textOffsetX = computeCursorTextWidthGui(fr, focused);
            cursorGuiX = focused.xPosition + padX + textOffsetX;
            cursorGuiY = bg ? focused.yPosition + (focused.height - 8) / 2 : focused.yPosition;
        } else if (screen instanceof GuiEditSign) {
            cursorGuiX = screenW / 2 - 24;
            cursorGuiY = 67 + 10;
        } else if (screen instanceof GuiScreenBook) {
            cursorGuiX = (screenW - 192) / 2 + 36;
            cursorGuiY = 32 + 12;
        } else {
            int cx = lastClickX >= 0 ? lastClickX / scale : screenW / 2;
            int cy = lastClickY >= 0 ? lastClickY / scale : screenH / 2;
            cursorGuiX = cx;
            cursorGuiY = cy;
        }

        int fontH = Math.max(8, fr.FONT_HEIGHT);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glTranslatef(0.0F, 0.0F, 400.0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int compBoxRight = cursorGuiX;
            if (hasComp) {
                int compW = fr.getStringWidth(comp);
                int drawX = Math.max(2, Math.min(cursorGuiX, screenW - compW - 4));
                int drawY = Math.max(2, Math.min(cursorGuiY, screenH - fontH - 3));

                int charStart = codePointOffsetToCharIndex(comp, compositionStart);
                int charEnd = codePointOffsetToCharIndex(comp, compositionStart + compositionLength);
                if (charEnd < charStart) {
                    charEnd = charStart;
                }

                @SuppressWarnings("null")
                String before = comp.substring(0, charStart);
                String target = comp.substring(charStart, charEnd);
                int beforeW = fr.getStringWidth(before);
                int targetW = fr.getStringWidth(target);

                // 背景ボックス (テキストバーと自然に馴染む半透明ダーク背景)
                Gui.drawRect(drawX - 2, drawY - 2, drawX + compW + 2, drawY + fontH + 2, 0xE810141C);
                // 外枠線
                Gui.drawRect(drawX - 2, drawY - 2, drawX + compW + 2, drawY - 1, 0xFF3B82F6);

                // 変換対象文節のハイライト
                if (targetW > 0) {
                    Gui.drawRect(drawX + beforeW, drawY - 1, drawX + beforeW + targetW, drawY + fontH, 0x702563EB);
                }

                // Minecraft の FontRenderer (MinFo 等を含む) で未確定文字列を描画
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                fr.drawStringWithShadow(comp, drawX, drawY, 0xFFFFFF);

                // 下線 (全体: 細線, 選択文節: シアン強調線)
                Gui.drawRect(drawX, drawY + fontH, drawX + compW, drawY + fontH + 1, 0xFF94A3B8);
                if (targetW > 0) {
                    Gui.drawRect(drawX + beforeW, drawY + fontH, drawX + beforeW + targetW, drawY + fontH + 1,
                            0xFF38BDF8);
                } else {
                    // カレット位置表示
                    int caretX = drawX + beforeW;
                    Gui.drawRect(caretX, drawY - 1, caretX + 1, drawY + fontH, 0xFF38BDF8);
                }
                compBoxRight = drawX + beforeW;
            }

            // SDL_EVENT_TEXT_EDITING_CANDIDATES による変換候補リストがある場合は同じフォントで描画
            if (hasCands) {
                @SuppressWarnings("null")
                int total = cands.length;
                int sel = selectedCandidate >= 0 && selectedCandidate < total ? selectedCandidate : 0;
                int page = sel / CANDIDATE_PAGE_SIZE;
                int startIdx = page * CANDIDATE_PAGE_SIZE;
                int endIdx = Math.min(total, startIdx + CANDIDATE_PAGE_SIZE);
                int count = endIdx - startIdx;

                if (count > 0) {
                    int rowH = fontH + 2;
                    String footer = (sel + 1) + " / " + total;
                    int maxW = fr.getStringWidth(footer) + 8;
                    for (int i = startIdx; i < endIdx; i++) {
                        String label = (i - startIdx + 1) + ". " + cands[i];
                        int w = fr.getStringWidth(label) + 8;
                        if (w > maxW) {
                            maxW = w;
                        }
                    }

                    int boxW = maxW;
                    int boxH = count * rowH + rowH + 4;
                    int boxX = Math.max(2, Math.min(compBoxRight, screenW - boxW - 2));
                    // 下側に十分な余白があれば下、無ければ(チャット欄など画面下端)上側に配置
                    int belowY = cursorGuiY + fontH + 4;
                    int boxY;
                    if (belowY + boxH <= screenH - 2) {
                        boxY = belowY;
                    } else {
                        boxY = Math.max(2, cursorGuiY - boxH - 4);
                    }

                    // 背景と枠線
                    Gui.drawRect(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF3B82F6);
                    Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, 0xF010141C);

                    for (int i = startIdx; i < endIdx; i++) {
                        int row = i - startIdx;
                        int ry = boxY + 2 + row * rowH;
                        if (i == sel) {
                            Gui.drawRect(boxX + 1, ry - 1, boxX + boxW - 1, ry + fontH + 1, 0x902563EB);
                        }
                        String numStr = (row + 1) + ". ";
                        int numW = fr.getStringWidth(numStr);
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        fr.drawStringWithShadow(numStr, boxX + 4, ry, i == sel ? 0x38BDF8 : 0x94A3B8);
                        fr.drawStringWithShadow(cands[i], boxX + 4 + numW, ry, i == sel ? 0xFFFF55 : 0xFFFFFF);
                    }

                    // フッター (ページ位置)
                    int fy = boxY + 2 + count * rowH + 1;
                    Gui.drawRect(boxX + 2, fy - 1, boxX + boxW - 2, fy, 0x60475569);
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    int fw = fr.getStringWidth(footer);
                    fr.drawStringWithShadow(footer, boxX + boxW - fw - 4, fy + 1, 0x94A3B8);
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static int codePointOffsetToCharIndex(String s, int cpOffset) {
        if (s == null || s.isEmpty() || cpOffset <= 0) {
            return 0;
        }
        try {
            int totalCp = s.codePointCount(0, s.length());
            if (cpOffset >= totalCp) {
                return s.length();
            }
            return s.offsetByCodePoints(0, cpOffset);
        } catch (Throwable t) {
            return Math.min(s.length(), Math.max(0, cpOffset));
        }
    }

    private static int computeCursorTextWidthGui(FontRenderer fr, GuiTextField focused) {
        try {
            String text = focused.getText();
            if (text == null || text.isEmpty()) {
                return 0;
            }
            int cp = Math.max(0, Math.min(text.length(), focused.getCursorPosition()));
            int scroll = getLineScrollOffset(focused);
            scroll = Math.max(0, Math.min(cp, scroll));
            int innerW = Math.max(1, focused.getWidth());
            String visibleBeforeCursor = fr.trimStringToWidth(text.substring(scroll, cp), innerW);
            return fr.getStringWidth(visibleBeforeCursor);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** @return {x, y, w, h, cursorOffset} (フレームバッファ px、上原点) */
    private static int[] computeAreaFramebufferPx(Minecraft mc, GuiScreen screen, GuiTextField focused) {
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int scale = Math.max(1, sr.getScaleFactor());

        if (focused != null) {
            FontRenderer fr = getFontRenderer(mc, focused);
            boolean bg = focused.getEnableBackgroundDrawing();
            int padX = bg ? 4 : 0;
            int cursorTextW = computeCursorTextWidthGui(fr, focused);
            String comp = compositionText;
            int compW = (comp != null && !comp.isEmpty() && fr != null) ? fr.getStringWidth(comp) : 0;
            int cursorGuiX = padX + cursorTextW;
            int cursorPx = cursorGuiX * scale;
            int w = Math.max(1, Math.max(focused.width, cursorGuiX + compW + 4) * scale);
            int h = Math.max(1, focused.height * scale);
            return new int[] { focused.xPosition * scale, focused.yPosition * scale, w, h,
                    Math.min(Math.max(0, cursorPx), Math.max(0, w - 1)) };
        }

        // 看板編集画面 (GuiEditSign)
        if (screen instanceof GuiEditSign) {
            int signX = (sr.getScaledWidth() / 2 - 48) * scale;
            int signY = 67 * scale;
            int signW = 96 * scale;
            int signH = 50 * scale;
            return new int[] { signX, signY, signW, signH, signW / 2 };
        }

        // 本と羽根ペン画面 (GuiScreenBook)
        if (screen instanceof GuiScreenBook) {
            int bookX = ((sr.getScaledWidth() - 192) / 2 + 36) * scale;
            int bookY = 32 * scale;
            int bookW = 116 * scale;
            int bookH = 120 * scale;
            return new int[] { bookX, bookY, bookW, bookH, 0 };
        }

        int cx = lastClickX >= 0 ? lastClickX : mc.displayWidth / 2;
        int cy = lastClickY >= 0 ? lastClickY : mc.displayHeight / 2;
        return new int[] { cx, cy, 50 * scale, 12 * scale, 0 };
    }

    private static GuiTextField resolveFocusedTextField(GuiScreen screen) {
        GuiTextField tracked = activeTextFieldRef.get();
        if (tracked != null) {
            if (tracked.isFocused() && tracked.getVisible()) {
                return tracked;
            }
            activeTextFieldRef = new WeakReference<>(null);
        }
        GuiTextField found = findFocusedTextField(screen);
        if (found != null) {
            activeTextFieldRef = new WeakReference<>(found);
        }
        return found;
    }

    private static int getLineScrollOffset(GuiTextField field) {
        Class<?> cls = field.getClass();
        Field f = LINE_SCROLL_FIELD_CACHE.get(cls);
        if (f == null && !LINE_SCROLL_FIELD_CACHE.containsKey(cls)) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (String name : new String[] { "lineScrollOffset", "field_146225_q" }) {
                    try {
                        Field candidate = c.getDeclaredField(name);
                        if (candidate.getType() == int.class) {
                            candidate.setAccessible(true);
                            f = candidate;
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (f != null) {
                    break;
                }
            }
            LINE_SCROLL_FIELD_CACHE.put(cls, f);
        }
        if (f != null) {
            try {
                return f.getInt(field);
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static FontRenderer getFontRenderer(Minecraft mc, GuiTextField field) {
        Class<?> cls = field.getClass();
        Field f = FONT_RENDERER_FIELD_CACHE.get(cls);
        if (f == null && !FONT_RENDERER_FIELD_CACHE.containsKey(cls)) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field candidate : c.getDeclaredFields()) {
                    if (FontRenderer.class.isAssignableFrom(candidate.getType())) {
                        try {
                            candidate.setAccessible(true);
                            f = candidate;
                            break;
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (f != null) {
                    break;
                }
            }
            FONT_RENDERER_FIELD_CACHE.put(cls, f);
        }
        if (f != null) {
            try {
                FontRenderer fr = (FontRenderer) f.get(field);
                if (fr != null) {
                    return fr;
                }
            } catch (Throwable ignored) {
            }
        }
        return mc.fontRenderer;
    }

    private static GuiTextField findFocusedTextField(GuiScreen screen) {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        return findFocusedInObject(screen, visited, 0);
    }

    private static GuiTextField findFocusedInObject(Object owner, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (owner == null || depth > 2 || visited.put(owner, Boolean.TRUE) != null) {
            return null;
        }
        Field[] fields = getInspectableFields(owner.getClass());
        for (Field f : fields) {
            try {
                Object v = f.get(owner);
                GuiTextField hit = pick(v, visited, depth + 1);
                if (hit != null) {
                    return hit;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Field[] getInspectableFields(Class<?> targetClass) {
        Field[] fields = FIELD_CACHE.get(targetClass);
        if (fields != null) {
            return fields;
        }
        ArrayList<Field> list = new ArrayList<>();
        for (Class<?> c = targetClass; c != null && c != Object.class; c = c.getSuperclass()) {
            String pkg = c.getName();
            if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("sun.")) {
                break;
            }
            for (Field f : c.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (Modifier.isStatic(f.getModifiers()) || t.isPrimitive() || t == String.class) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    list.add(f);
                } catch (Throwable ignored) {
                }
            }
        }
        fields = list.toArray(new Field[0]);
        FIELD_CACHE.put(targetClass, fields);
        return fields;
    }

    private static GuiTextField pick(Object v, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (v == null) {
            return null;
        }
        if (v instanceof GuiTextField) {
            GuiTextField tf = (GuiTextField) v;
            return (tf.isFocused() && tf.getVisible()) ? tf : null;
        }
        if (v instanceof GuiTextField[]) {
            for (GuiTextField t : (GuiTextField[]) v) {
                if (t != null && t.isFocused() && t.getVisible()) {
                    return t;
                }
            }
            return null;
        }
        if (v instanceof Object[]) {
            for (Object o : (Object[]) v) {
                GuiTextField hit = pick(o, visited, depth);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (v instanceof Collection) {
            for (Object o : (Collection<?>) v) {
                GuiTextField hit = pick(o, visited, depth);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (v instanceof Map) {
            for (Object o : ((Map<?, ?>) v).values()) {
                GuiTextField hit = pick(o, visited, depth);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (depth <= 2) {
            String clsName = v.getClass().getName();
            if (!clsName.startsWith("java.") && !clsName.startsWith("javax.")
                    && !clsName.startsWith("net.minecraft.world.") && !clsName.startsWith("net.minecraft.entity.")
                    && !clsName.startsWith("net.minecraft.item.") && !clsName.startsWith("net.minecraft.block.")
                    && !clsName.startsWith("net.minecraft.client.Minecraft")
                    && !clsName.startsWith("net.minecraft.client.renderer.")) {
                return findFocusedInObject(v, visited, depth);
            }
        }
        return null;
    }

    private static void runOnMain(Runnable r) {
        try {
            execMethod.invoke(null, r);
        } catch (Throwable ignored) {
        }
    }

    private ImeCompat() {
    }
}
