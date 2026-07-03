# 🛠️ CrossTie

專為 Minecraft 1.7.10 的 RTM (RealTrainMod) 系綜合優化與相容性修補 Mod。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 專案簡介
CrossTie 將跨越多個 Mod 的**渲染減負、更新頻率抑制、相容性修復**集中在單一 JAR 中提供。支援的 Mod 包括 RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod 等。

> 💡 **自動偵測功能**: 目標 Mod 將於啟動時自動偵測，僅在存在時才啟用相應的修補程式。

---

## 📊 狀態
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=下載量%28最新發布%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 執行環境 & 建置狀態
| 項目 | 狀態 / 版本 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.0` |
| **前置 Mod** | `UniMixins 0.3.1+` |
| **建置系統** | RetroFuturaGradle 2.0.2 |
| **最後確認** | `2026-06-29` |

### 🔍 內部結構索引
* **Mixin 控制**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **可偵測 Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 推薦配置

為流暢運行而推薦的 Mod 版本配置。

| Mod 名稱 | 推薦版本 | 類別 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **本體** |
| **UniMixins** | `0.3.1` | **必備** |
| **KaizPatchX** | `1.10.0` | 推薦 |
| **Angelica** | `2.1.42+` | 推薦 |
| **GTNHLib** | `0.11.18+` | 推薦 |
| **Hodgepodge** | `2.7.162+` | 選用 |
| **ArchaicFix** | `0.8.0+` | 選用 |
| **ShaderFixer** | `5.4+` | 選用 |

---

## ⚡ 本 Mod 的作用

CrossTie 綜合解決了 RTM 相關 Mod 群與效能優化 Mod 群之間發生的以下 **3個核心問題**。

1. **🏃 FPS 優化**
   * [RTM] 大型鐵軌 (LargeRail) 的距離剔除與視錐體剔除
   * [RTM] 大型鐵軌的區塊單位渲染聚合
   * [RTM] 大型鐵軌 TESR 根據距離的渲染頻率節流
   * [RTM] 鐵軌細分曲面迴圈優化
   * [RTM] 電線及支柱連接判定結果快取
   * [RTM] 招牌選擇 GUI 虛擬滾動化，改善開啟延遲
   * [RTM] 號誌燈/平交道取消剔除，維持遠景渲染
   * [NGTScriptUtil] 腳本執行 (Invocable) 的快取優化
   * [RailMapCustom] 鐵路地圖快取優化
   * [MCTE] 世界方塊差異集合優化
   * [Angelica] RenderGlobal.displayList 的原生優化
   * [KaizPatch, NGTScriptUtil, Angelica] 腳本發出的 GL 呼叫重新導向及快取優化
2. **⏳ TPS / 伺服器負載優化**
   * [RTM] 降低距離 256m 以上列車實體的客戶端更新頻率
   * [RTM] 優化列車速度的 DataWatcher 同步以降低網路負載
   * [RTM] 快取列車 onUpdate 內重複的 getBlock() 呼叫
   * [GTNHLib] 執行緒安全的物件池化
3. **🤝 相容性與渲染錯誤修復**
   * [Angelica] 修復開啟光影時原版雲層重複渲染的問題
   * [Angelica] 修復開啟光影時水面渲染距離異常的問題
   * [Angelica, RTM] 修復區塊重建時鐵軌 TESR 光照不更新的問題
   * [Angelica] 修復載入畫面的紋理狀態快取問題
   * [OptiFine, RTM] 修復大型鐵軌 UV 座標損壞（綠色直線）的問題
   * [OptiFine, RTM] 修復電線渲染時法線扭曲導致在光影環境下透明化的問題
   * [OptiFine, RTM] 修復陰影通道(shadow pass)中電線不渲染而消失的問題
   * [GTNHLib] 修復玻璃板及方塊圖示顯示與獲取失敗的回退
   * [Hodgepodge] 避免 Guava 類別載入器衝突
   * [LiteLoader, MacroMod] 權限管理及核心的相容性修復
   * [MCTE] 修復微縮方塊及物品微縮的動態光照
   * [KaizPatch] ModelLoaderKt 回退修復
4. **✨ 新功能追加**
   * [RTM] 新增免重啟的重新載入模型包功能（在設定或 mods→CrossTie→RTM→reloadPacks）※可能仍有臭蟲，但大體正常運作。
   * [RTM] 能夠刪除頭頂上方兩格處的架線（設定熱鍵+右鍵）※手中不能拿物品。
   * [RTM] 新增在關門時不會傳到車外的車內廣播音訊 API

---

## 🏗️ 架構

CrossTie 擁有**三層修補機制**，在適當階段安全介入。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
作為 `IFMLLoadingPlugin`，在 Minecraft 啟動後最早階段執行。
* **ModDetector**: 掃描 `mods/` 資料夾，從 JAR/ZIP/litemod 檔名自動偵測安裝的 Mod。
* **MinFo 偵測 + Angelica 設定自動調整**: 若偵測到 MinFo，強制將 `config/angelica-modules.cfg` 中的 `B:enableFontRenderer` 改寫為 `false`。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
在類別載入時，於 Mixin 階段之前直接修改位元組碼。完全避免了 Mixin 無法處理的「載入順序問題」以及「MixinTargetAlreadyLoadedException」。

| 目標類別 / 方法 | 內容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | 將 `nhlib$getParticleIcon` 重新導向至 `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | 將 `tweakPaneIcons` 等玻璃板圖示解析重新導向至 `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | 將 `setupIcons` 替換為 `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | 將 `getString(s)` → 替換為 `s.intern()` (避免 Guava 類別載入器衝突) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)` (支援無 Nashorn 環境) |
| **MacroMod** `MacroModPermissions` | 移除所有方法中的 `tamperCheck()` 呼叫 |
| **LiteLoader** `PermissionsManagerClient` | `tamperCheck()` → 設為 no-op 空操作 |
| **SplashProgress$3** (`SplashProgress$3`) | 在 `run()` 頭部注入透過反射的 GL 狀態重設 (`GL_TEXTURE_2D` + `glColor4f`) |

### 3. Mixin (動態套用)
`CrossTieMixinPlugin` 根據偵測到的已安裝 Mod 動態套用必要的 Mixin。

<details>
<summary>🔍 各 Mod 的 Mixin 套用詳情列表（點擊展開）</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * 在渲染 `hi03ExpressRailwayRail` 時，繞過 Angelica 的顯示清單並使用舊的 OpenGL 路徑
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * 修復載入畫面紋理狀態快取問題

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib 始終)
    * 執行緒安全的物件池化
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * 修復玻璃板及方塊圖示顯示與獲取回退

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * 優化 Invocable 快取
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * 使用 `ScriptGlRedirector` 攔截 `AngelicaCompat.transformScript` (GL 呼叫重新導向 + 快取)
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * 向 `ModelPackManager.getScript` 套用 `ScriptGlRedirector`
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * 鐵路地圖快取優化
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * 世界方塊差異集合優化
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * 修復微縮方塊/物品微縮的動態光照
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * 列車速度同步及實體更新優化
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * 優化電線渲染及鐵軌立柱連接快取
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * 大型鐵軌渲染優化及區塊批次處理
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * 鐵軌零件渲染器優化

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * 權限管理及核心相容性修復

</details>

---

## 📦 支援 Mod 詳情

| Mod | 偵測名稱 | 主要修補・對應內容 |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | 渲染優化，減少更新，GL 重新導向 |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil 相容，GL 重新導向 |
| **MCTE** | `MCTE` | 微縮渲染修復，動態光照 |
| **KaizPatch** | `KaizPatch` | 聯動 Angelica，腳本快取 |
| **Angelica** | `Angelica` / `AngelicaGlsm` | 顯示清單衝突修復，設定自動調整 |
| **Bamboo** | `Bamboo` | 渲染剔除，降低更新頻率（向下相容） |
| **IntelliInput** | `IntelliInput` | IME 回呼穩定化（向下相容） |
| **GTNHLib** | `GTNHLib` | 圖示解析，執行緒安全化 |
| **Hodgepodge** | `Hodgepodge` | 避免 Guava 類別載入器衝突 |
| **LiteLoader** | `LiteLoader` | 權限管理修復 |
| **MacroMod** | `MacroMod` | 移除 `tamperCheck`，權限修復 |
| **Keybind Mod** | *(隨附偵測)* | 移除 `tamperCheck` |
| **RailMapCustom** | `RailMapCustom` | 鐵路地圖快取 |

---

## 📥 安裝方法

1. 將下載的 `CrossTie-*.jar` 放入 `mods` 資料夾中。
2. 將必備的 `UniMixins 0.3.1+` 放入 `mods` 資料夾中。
3. 根據需要新增 RTM / Angelica / Bamboo / IntelliInput 等目標 Mod。
4. 像往常一樣啟動遊戲即可。

---

## 🛠️ 建置與開發

### 🧱 建置步驟
```bash
./gradlew build --no-daemon
```