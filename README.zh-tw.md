# 🛠️ CrossTie

適用於 Minecraft 1.7.10 的 RTM（RealTrainMod）系綜合最佳化與相容性補丁 Mod。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 專案概要
CrossTie 將 RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod 等多個 Mod 的**減少渲染負載、抑制更新頻率、修正相容性問題**等功能，整合在單一 JAR 檔案中提供。

> 💡 **自動偵測功能**: 目標 Mod 會在遊戲啟動時自動偵測，只有在該 Mod 存在時才會啟用對應的補丁。

---

## 📊 狀態
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=%E4%B8%8B%E8%BC%89%E6%AC%A1%E6%95%B8%28%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 執行環境 & 建置狀態
| 項目 | 狀態 / 版本 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `8` |
| **必要 Mod** | `UniMixins 0.3.1+` |
| **建置系統** | RetroFuturaGradle 1.4.1 |
| **最後確認** | `2026-06-19` |

### 🔍 內部結構索引
* **Mixin 控制**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **可偵測 Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 推薦配置

為了讓遊戲能更流暢執行，建議使用以下 Mod 版本配置。

| Mod 名稱 | 推薦版本 | 分類 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha6` | **主體** |
| **UniMixins** | `0.3.1` | **必要** |
| **KaizPatchX** | `1.10.0` | 推薦 |
| **Angelica** | `2.1.42+` | 推薦 |
| **GTNHLib** | `0.11.18+` | 推薦 |
| **Hodgepodge** | `2.7.162+` | 選配 |
| **ArchaicFix** | `0.8.0` | 選配 |
| **ShaderFixer** | `5.4` | 選配 |

---

## ⚡ 本 Mod 的主要功能

CrossTie 旨在一次解決 RTM 相關 Mod 群與效能最佳化 Mod 群之間產生的**三個核心問題**：

1. **🏃 FPS 最佳化**
   * 畫面渲染剔除（Culling）、抑制更新頻率、切換至立即渲染模式。
2. **⏳ TPS 最佳化**
   * 降低實體（Entity）與方塊實體（TileEntity）的更新頻率（間歇更新）。
3. **🤝 相容性修正**
   * 解決 Angelica 與 RTM 之間的顯示列表（Display List）衝突。
   * 重定向 GL 呼叫。
   * 避免 Mod 之間的類別載入器（Class Loader）衝突。

---

## 🏗️ 架構設計

CrossTie 採用**三層補丁機制**，會在最適當的階段安全地介入系統。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
作為 `IFMLLoadingPlugin` 在 Minecraft 啟動後的最早階段執行。
* **ModDetector**: 掃描 `mods/` 資料夾，從 JAR / ZIP / litemod 的檔案名稱中自動偵測已安裝的 Mod。
* **偵測 MinFo 並自動調整 Angelica 設定**: 當偵測到 MinFo 時，會強制將 `config/angelica-modules.cfg` 中的 `B:enableFontRenderer` 修改為 `false`。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
在 Mixin 階段之前、類別載入時直接修改位元組碼（Bytecode）。這能完美避免 Mixin 無法處理的「載入順序問題」以及「MixinTargetAlreadyLoadedException」。

| 目標類別 / 方法 | 內容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | 將 `nhlib$getParticleIcon` 重定向至 `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | 將 `tweakPaneIcons` 等玻璃板（Glass Pane）的圖標解析重定向至 `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | 將 `setupIcons` 取代為 `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | 將 `getString(s)` 取代為 `s.intern()` (規避 Guava 類別載入器衝突) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)` (對應缺乏 Nashorn 的環境) |
| **MacroMod** `MacroModPermissions` | 刪除所有方法中對 `tamperCheck()` 的呼叫 |
| **LiteLoader** `PermissionsManagerClient` | 將 `tamperCheck()` 轉為無操作（no-op）化 |
| **SplashProgress$3** (`SplashProgress$3`) | 在 `run()` 的開頭，透過反射注入 GL 狀態重設（`GL_TEXTURE_2D` + `glColor4f`） |

### 3. Mixin (動態套用)
`CrossTieMixinPlugin` 會根據偵測到的已安裝 Mod，動態套用所需的 Mixin。

<details>
<summary>🔍 各 Mod 的 Mixin 套用詳細清單（點擊展開）</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * 當渲染 `hi03ExpressRailwayRail` 时，繞過 Angelica 的顯示列表，使用舊有的 OpenGL 路徑。
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * 修正啟動畫面（Splash Screen）的材質狀態快取問題。

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib 常駐)
    * 執行緒安全的物件池（Object Pooling）化。
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * 修正玻璃板與方塊的圖標顯示、獲取及後備（Fallback）方案。

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * 最佳化 Invocable 的快取機制。
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * 使用 `ScriptGlRedirector` 攔截 `AngelicaCompat.transformScript`（重定向 GL 呼叫 + 快取）。
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * 對 `ModelPackManager.getScript` 套用 `ScriptGlRedirector`。
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * 最佳化鐵路地圖（RailMap）的快取。
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * 最佳化世界方塊差分設定。
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * 修正微縮方塊與微縮物品的動態光源。
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * 最佳化列車速度同步以及實體更新。
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * 將配線渲染及線路柱連接進行快取化處理。
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * 最佳化大型鐵軌渲染與區塊批次處理（Chunk Batching）。
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * 最佳化鐵軌零件渲染器。

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * 修正權限周邊與核心的相容性。

</details>

---

## 📦 支援 Mod 詳細資訊

| Mod 名稱 | 偵測名稱 | 主要補丁與對應內容 |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | 渲染最佳化、降低更新頻率、GL 重定向 |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil 相容性、GL 重定向 |
| **MCTE** | `MCTE` | 修正微縮模型渲染、動態光源 |
| **KaizPatch** | `KaizPatch` | Angelica 整合連動、腳本快取 |
| **Angelica** | `Angelica` / `AngelicaGlsm` | 修正顯示列表衝突、自動調整設定 |
| **Bamboo** | `Bamboo` | 畫面渲染剔除、抑制更新頻率 (向後相容) |
| **IntelliInput** | `IntelliInput` | 穩定 IME 回呼機制 (向後相容) |
| **GTNHLib** | `GTNHLib` | 圖標解析、執行緒安全化 |
| **Hodgepodge** | `Hodgepodge` | 避免 Guava 類別載入器衝突 |
| **LiteLoader** | `LiteLoader` | 修正權限管理系統 |
| **MacroMod** | `MacroMod` | 移除 `tamperCheck`、修正權限問題 |
| **Keybind Mod** | *(隨附偵測)* | 移除 `tamperCheck` |
| **RailMapCustom** | `RailMapCustom` | 鐵路地圖快取 |

---

## 📥 安裝方法

1. 將下載的 `CrossTie-*.jar` 檔案放入 `mods` 資料夾中。
2. 將必要的 `UniMixins 0.3.1+` 檔案放入 `mods` 資料夾中。
3. 根據您的需求，額外加入 RTM / Angelica / Bamboo / IntelliInput 等目標 Mod。
4. 照常啟動遊戲即可。

---

## 🛠️ 建置與開發

### 🧱 建置步驟
```bash
./gradlew build --no-daemon