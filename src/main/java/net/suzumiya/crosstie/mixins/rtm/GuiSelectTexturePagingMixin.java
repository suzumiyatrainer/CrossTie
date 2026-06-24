package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.gui.GuiButtonSelectTexture;
import jp.ngt.rtm.modelpack.texture.ITextureHolder;
import jp.ngt.rtm.modelpack.texture.TextureProperty;
import net.minecraft.client.gui.GuiScreen;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * GuiSelectTexture の仮想スクロール最適化 Mixin。
 *
 * <h3>問題</h3>
 * <p>元の {@code initGui()} はテクスチャ全件分のボタンを一気に {@code buttonList} へ
 * 追加するため、数百〜数千枚のサインボードテクスチャがある環境では
 * GUI を開くのに非常に時間がかかる。
 *
 * <h3>解決策</h3>
 * <p>表示範囲の行数分だけボタンを生成し、スクロール時に再生成する
 * 仮想スクロール方式に置き換える。これにより起動コストが O(全件) → O(画面行数) に下がる。
 *
 * <h3>設定</h3>
 * <p>{@link CrossTieConfig#signboardGuiPagingEnabled} が {@code false} の場合は
 * 元の全件生成ロジックで動作する。
 */
@SideOnly(Side.CLIENT)
@Mixin(targets = "jp.ngt.rtm.gui.GuiSelectTexture", remap = false)
public abstract class GuiSelectTexturePagingMixin extends GuiScreen {

    // ---- @Shadow : GuiSelectTexture のプライベートフィールドを参照 ----

    @Shadow
    public ITextureHolder holder;

    @Shadow
    private List<TextureProperty> properties;

    @Shadow
    private int currentScroll;

    @Shadow
    private int prevScroll;

    @Shadow
    private int uCount;

    @Shadow
    private int vCount;

    /** GuiSelectTexture 内の {@code private final int SPACING_TEXT_Y = 64} */
    @Shadow
    private int SPACING_TEXT_Y;

    // ---- 仮想スクロール用追加フィールド ----

    /** セル幅（ピクセル） */
    @Unique
    private int crosstie$cellW;

    /** セル高（ピクセル） */
    @Unique
    private int crosstie$cellH;

    /** ボタン列の X 軸センタリングオフセット（ピクセル） */
    @Unique
    private int crosstie$offsetX;

    // =========================================================
    //  initGui() ：全件生成 → 仮想スクロール（表示行のみ生成）
    // =========================================================

    /**
     * サインボード選択 GUI のボタン初期化を仮想スクロール方式に置き換える。
     *
     * <p>元の実装は全テクスチャ数分のボタンを {@code buttonList} に追加するが、
     * 本実装では {@code vCount} 行 + バッファ 1 行分のみ生成する。
     *
     * @author CrossTie (Antigravity)
     * @reason 全ボタン一括生成による GUI 開き遅延を仮想スクロールで解消する
     */
    @Overwrite
    public void initGui() {
        // 元の GuiSelectTexture#initGui() 同様、super.initGui() は呼ばない
        int x = !this.properties.isEmpty() ? this.properties.get(0).getUWidthInGui() : this.width;
        int y = !this.properties.isEmpty() ? this.properties.get(0).getVHeightInGui() : this.height;

        this.uCount = Math.max(this.width / x, 1);
        this.vCount = Math.max(this.height / (y + SPACING_TEXT_Y), 1);

        this.crosstie$cellW   = x;
        this.crosstie$cellH   = y;
        this.crosstie$offsetX = (this.width - (x * this.uCount)) / 2;

        this.buttonList.clear();

        if (CrossTieConfig.signboardGuiPagingEnabled) {
            // 仮想スクロール：画面に収まる行数 + バッファ 1 行分のみ生成
            crosstie$buildPage(this.currentScroll);
        } else {
            // 設定無効：元通り全件生成
            crosstie$buildAll();
        }
    }

    // =========================================================
    //  renewButton() ：仮想スクロール時にボタンを再生成する
    //
    //  元の実装は全ボタンを moveButton() でスライドさせるが、
    //  仮想スクロール時は表示ページを再生成する方式に差し替える。
    // =========================================================

    /**
     * スクロール後のボタン更新処理を仮想スクロールに対応させる。
     * <p>{@code handleMouseInput()} が {@code renewButton()} を呼ぶ直前に割り込み、
     * 仮想スクロール有効時はボタンを再生成して元の moveButton ロジックをキャンセルする。
     */
    @Inject(method = "renewButton", at = @At("HEAD"), cancellable = true, require = 1)
    private void crosstie$renewButtonVirtual(int scroll, CallbackInfo ci) {
        if (!CrossTieConfig.signboardGuiPagingEnabled) {
            return; // 設定無効時は元の moveButton ロジックに任せる
        }

        // 仮想スクロール：現在のスクロール行から表示ページ分を再生成
        if (this.currentScroll != this.prevScroll) {
            crosstie$buildPage(this.currentScroll);
        }

        ci.cancel();
    }

    // =========================================================
    //  内部ヘルパー
    // =========================================================

    /**
     * 指定スクロール行から「表示行（vCount）+ バッファ 1 行」分のボタンを生成し
     * {@code buttonList} を差し替える（仮想スクロール用）。
     *
     * @param scrollRow 先頭に表示する行インデックス（0 始まり）
     */
    @Unique
    private void crosstie$buildPage(int scrollRow) {
        this.buttonList.clear();
        final int x    = this.crosstie$cellW;
        final int y    = this.crosstie$cellH;
        final int rows = this.vCount + 1; // バッファ行 +1

        for (int v = 0; v < rows; v++) {
            final int row = scrollRow + v;
            for (int u = 0; u < this.uCount; u++) {
                final int index = row * this.uCount + u;
                if (index >= this.properties.size()) return;

                final TextureProperty prop = this.properties.get(index);
                final float scale = (prop.width > prop.height)
                        ? (float) x / prop.width
                        : (float) y / prop.height;
                final int w    = (int) (prop.width  * scale);
                final int h    = (int) (prop.height * scale);
                final int xPos = x * u + ((x - w) / 2) + this.crosstie$offsetX;
                final int yPos = (y * v + (v * SPACING_TEXT_Y)) + ((y - h) / 2);
                this.buttonList.add(new GuiButtonSelectTexture(index, xPos, yPos, w, h, prop));
            }
        }
    }

    /**
     * 全テクスチャ分のボタンを生成する
     * ({@link CrossTieConfig#signboardGuiPagingEnabled} 無効時のフォールバック)。
     */
    @Unique
    private void crosstie$buildAll() {
        final int x = this.crosstie$cellW;
        final int y = this.crosstie$cellH;
        final int yCount = (this.properties.size() / this.uCount) + 1;

        for (int v = 0; v < yCount; v++) {
            for (int u = 0; u < this.uCount; u++) {
                final int index = v * this.uCount + u;
                if (index >= this.properties.size()) break;

                final TextureProperty prop = this.properties.get(index);
                final float scale = (prop.width > prop.height)
                        ? (float) x / prop.width
                        : (float) y / prop.height;
                final int w    = (int) (prop.width  * scale);
                final int h    = (int) (prop.height * scale);
                final int xPos = x * u + ((x - w) / 2) + this.crosstie$offsetX;
                final int yPos = (y * v + (v * SPACING_TEXT_Y)) + ((y - h) / 2);
                this.buttonList.add(new GuiButtonSelectTexture(index, xPos, yPos, w, h, prop));
            }
        }
    }
}
