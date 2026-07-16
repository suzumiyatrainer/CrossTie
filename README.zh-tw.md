# 🛠️ CrossTie

專為 Minecraft 1.7.10 提供的 RTM (RealTrainMod) 相關模組綜合最佳化與相容性修補模組。

> ⚠️ **構建與開發注意事項**:
> `src/main/java/jp/kaiz/atsassistmod/block/tileentity/TileEntityIFTTT.java` 是一個**僅用於編譯的虛構類（Stub）**，用於防止在 CI 環境（如 GitHub Actions 等）下發生編譯錯誤。
> 該類在構建最終發布 JAR 檔案時會自動被排除（exclude），因此不會影響遊戲運行時的行為。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 專案簡介
CrossTie 將橫跨多個模組（例如 RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod、WorldEdit、ProjectRed、CustomNPC+ 等）的**渲染負載降低、更新頻率抑制與相容性修復**功能整合在單一個 JAR 檔案中提供。

> 💡 **自動偵測功能**: 啟動時會自動偵測目標模組，僅在模組存在時啟用對應的修補程式。

---

## 📊 狀態
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=%E4%B8%8B%E8%BC%89%E9%87%8F%28%E6%9C%80%E6%96%B0%E7%99%BC%E5%B8%83%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 執行環境與建置狀態
| 項目 | 狀態 / 版本 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.1` |
| **必備模組** | `UniMixins 0.3.1+` |
| **建置系統** | RetroFuturaGradle 2.0.2 |
| **最後確認** | `2026-07-16` |

### 🔍 內部結構索引
* **Mixin 控制**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **延遲 Mixin 控制**: [`CrossTieLateMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieLateMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **可偵測模組**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`, `WorldEdit`, `ProjectRed`, `CustomNPC+`, `ATSAssist`, `SignPicture`, `ArchitectureCraft`

---

## 🚀 推薦配置

為確保流暢運作推薦的模組版本配置。

| 模組名稱 | 推薦版本 | 類別 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **核心** |
| **UniMixins** | `0.3.1` | **必備** |
| **KaizPatchX** | `1.10.0` | 推薦 |
| **Angelica** | `2.1.49` | 推薦 |
| **GTNHLib** | `0.11.23+` | 推薦 |
| **Hodgepodge** | `2.7.171+` | 選用 |
| **ArchaicFix** | `0.8.0+` | 選用 |
| **ShaderFixer** | `5.4+` | 選用 |

---

## ⚡ 本模組的功能

CrossTie 統整解決了 RTM 相關模組與效能最佳化模組之間發生的以下 **4 項核心項目**。

1. **🏃 FPS 最佳化**
   * LargeRail 渲染最佳化、腳本執行最佳化、顯示列表（Display List）最佳化等。
   * 詳情: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

2. **⏳ TPS / 伺服器負載最佳化**
   * 列車實體更新頻率最佳化、網路負載降低、物件池最佳化等。
   * 詳情: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

3. **🤝 相容性與渲染錯誤修復**
   * 修復在 Angelica 或 OptiFine 環境下的 RTM 渲染錯誤，以及其他周邊模組的衝突。
   * 詳情:
     * [`doc/Angelica・GTNHLib関連互換性修正.md`](./doc/Angelica・GTNHLib関連互換性修正.md)
     * [`doc/OptiFine・FastCraft関連互換性修正.md`](./doc/OptiFine・FastCraft関連互換性修正.md)
     * [`doc/RTM・NGTLib関連_バグ修正.md`](./doc/RTM・NGTLib関連_バグ修正.md)
     * [`doc/その他周辺Mod互換性修正.md`](./doc/その他周辺Mod互換性修正.md)

4. **✨ 新功能新增**
   * 免重啟重新載入模型包功能、電車線刪除功能、車廂內廣播用音效 API 等。
   * 詳情: [`doc/新規機能の使い方/`](./doc/新規機能の使い方/)

---

## 🏗️ 架構

CrossTie 具有 **3 層修補機制**，在合適的階段安全地介入。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
作為 `IFMLLoadingPlugin`，在 Minecraft 啟動後的最早階段執行。
* **ModDetector**: 掃描 `mods/` 資料夾，根據 JAR/ZIP/litemod 的檔案名稱自動偵測已安裝的模組。
* **MinFo 偵測與 Angelica 設定自動調整**: 若偵測到 MinFo，則強制將 `config/angelica-modules.cfg` 中的 `B:enableFontRenderer` 改寫為 `false`。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
在類別載入時，早於 Mixin 階段直接修改位元組碼。完全避免了 Mixin 無法處理的「載入順序問題」與「MixinTargetAlreadyLoadedException」。

| 目標類別 / 方法 | 內容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | 將 `nhlib$getParticleIcon` 重新導向到 `GtnhLibIconCompat` *(舊版本: GTNHLib 0.10.0+ 中已廢棄，自動跳過)* |
| **Angelica CTM** `MixinRenderBlocks` | 將 `tweakPaneIcons` 等玻璃板圖示解析重新導向到 `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | 將 `setupIcons` 替換為 `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | 將 `getString(s)` 替換為 `s.intern()`（避免 Guava 類別載入器衝突） |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)`（適配缺少 Nashorn 的環境） |

### 3. Mixin (動態套用)
`CrossTieMixinPlugin` 會根據偵測到的已安裝模組，動態套用所需的 Mixin。
為避免早期類別載入衝突，部分 Mixin 透過 `CrossTieLateMixinPlugin` 進行延遲載入（例如：ProjectRed）。

詳細的修補內容，請參閱 `doc/` 目錄下的各文件。

---

## 📥 安裝方法

1. 將下載的 `CrossTie-*.jar` 放入 `mods` 資料夾。
2. 將必備前置 `UniMixins 0.3.1+` 放入 `mods` 資料夾。
3. 根據需求，新增 RTM / Angelica / Bamboo / IntelliInput 等目標模組。
4. 像往常一樣啟動遊戲。

---

## 🛠️ 建置與開發

本專案使用 **RetroFuturaGradle (RFG)** 作為建置系統。

### 🧱 建置步驟
標準建置步驟。編譯後的 `.jar` 檔案將生成在 `build/libs/` 目錄下。
```bash
./gradlew build --no-daemon
```

### 💻 開發環境設定

#### IntelliJ IDEA
1. 在命令提示字元等執行以下命令，生成 IDEA 用的專案檔案。
```bash
./gradlew idea
```
2. 在 IntelliJ IDEA 中打開資料夾並匯入專案。

#### Eclipse
1. 執行以下命令，生成 Eclipse 用的專案檔案。
```bash
./gradlew eclipse
```
2. 在 Eclipse 中透過「將現有專案匯入至工作區」匯入專案。

### ▶️ 在開發環境中執行
您可以使用 IDE 自動生成的執行設定（Run Configuration），或透過以下命令啟動測試客戶端：
```bash
./gradlew runClient
```