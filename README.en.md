# 🛠️ CrossTie

Comprehensive optimization and compatibility patch Mod for RTM (RealTrainMod) on Minecraft 1.7.10.

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 Project Overview
CrossTie provides **rendering load reduction, update frequency suppression, and compatibility fixes** across multiple Mods—such as RTM / NGTLib / MCTE (KaizPatchX), Angelica, Bamboo, IntelliInput, GTNHLib, Hodgepodge, and LiteLoader / Macro / Keybind Mod—all within a single JAR.

> 💡 **Auto-Detection**: Target Mods are automatically detected at startup, and corresponding patches are enabled only if they are present.

---

## 📊 Status
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=Downloads%28latest%20release%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 Environment & Build Status
| Item | Status / Version |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `8` |
| **Required Mod** | `UniMixins 0.3.1+` |
| **Build System** | RetroFuturaGradle 1.4.1 |
| **Last Verified** | `2026-06-19` |

### 🔍 Internal Structure Index
* **Mixin Control**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **Detectable Mods**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 Recommended Configuration

Recommended Mod versions for optimal performance.

| Mod Name | Recommended Version | Category |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha6` | **Main** |
| **UniMixins** | `0.3.1` | **Required** |
| **KaizPatchX** | `1.10.0` | Recommended |
| **Angelica** | `2.1.42+` | Recommended |
| **GTNHLib** | `0.11.18+` | Recommended |
| **Hodgepodge** | `2.7.162+` | Optional |
| **ArchaicFix** | `0.8.0` | Optional |
| **ShaderFixer** | `5.4` | Optional |

---

## ⚡ What it does

CrossTie solves the following **three core problems** that occur between RTM-related Mods and performance-enhancing Mods.

1. **🏃 FPS Optimization**
   * Rendering culling, update frequency suppression, and switching to immediate rendering.
2. **⏳ TPS Optimization**
   * Thinning updates for Entities and TileEntities.
3. **🤝 Compatibility Fixes**
   * Resolving display list conflicts between Angelica and RTM.
   * Redirecting GL calls.
   * Avoiding class loader conflicts between Mods.

---

## 🏗️ Architecture

CrossTie features a **3-layer patch mechanism**, intervening safely at the appropriate phase.

### 1. ASM CoreMod (`CrossTieCorePlugin`)
Runs as an `IFMLLoadingPlugin` in the earliest phase immediately after Minecraft starts.
* **ModDetector**: Scans the `mods/` folder and automatically detects installed Mods from JAR/ZIP/litemod filenames.
* **MinFo Detection + Angelica Auto-Adjustment**: If MinFo is detected, it forcibly rewrites `B:enableFontRenderer` to `false` in `config/angelica-modules.cfg`.

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
Directly rewrites bytecode during class loading, before the Mixin phase. This completely avoids "loading order issues" and `MixinTargetAlreadyLoadedException` that Mixins cannot handle.

| Target Class / Method | Content |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | Redirects `nhlib$getParticleIcon` to `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | Redirects glass pane icon resolution (e.g., `tweakPaneIcons`) to `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | Replaces `setupIcons` with `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | Replaces `getString(s)` with `s.intern()` (Avoids Guava class loader conflicts) |
| **NGTLib/RTM** `ScriptUtil` | Replaces `doScript(String)` with `ScriptUtilFallback.doScript(String)` (Support for environments without Nashorn) |
| **MacroMod** `MacroModPermissions` | Removes `tamperCheck()` calls from all methods |
| **LiteLoader** `PermissionsManagerClient` | Makes `tamperCheck()` a no-op |
| **SplashProgress$3** (`SplashProgress$3`) | Injects GL state reset (`GL_TEXTURE_2D` + `glColor4f`) via reflection at the start of `run()` |

### 3. Mixin (Dynamic Application)
`CrossTieMixinPlugin` dynamically applies only the necessary Mixins based on the detected installed Mods.

<details>
<summary>🔍 Detailed list of Mixin applications per Mod (Click to expand)</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * When rendering `hi03ExpressRailwayRail`, avoids Angelica's display lists and uses the legacy OpenGL path.
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * Fixes texture state caching issues on the splash screen.

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib always)
    * Thread-safe object pooling.
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * Fixes icon display and retrieval fallbacks for glass panes and blocks.

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * Optimization of Invocable cache.
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * Intercepts `AngelicaCompat.transformScript` with `ScriptGlRedirector` (GL call redirection + caching).
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * Applies `ScriptGlRedirector` to `ModelPackManager.getScript`.
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * Optimization of rail map cache.
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * Optimization of world block difference sets.
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * Fixes dynamic lighting for miniature blocks and miniature items.
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * Optimization of train speed synchronization and entity updates.
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * Caching for wiring rendering and track pole connections.
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * Optimization of large rail rendering and chunk batch processing.
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * Optimization of rail parts renderer.

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * Compatibility fixes for permissions and core.

</details>

---

## 📦 Supported Mod Details

| Mod | Detection Name | Main Patch / Content |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | Rendering optimization, update thinning, GL redirection |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil compatibility, GL redirection |
| **MCTE** | `MCTE` | Miniature rendering fixes, dynamic lighting |
| **KaizPatch** | `KaizPatch` | Angelica integration, script caching |
| **Angelica** | `Angelica` / `AngelicaGlsm` | Display list conflict fixes, auto-adjustment of settings |
| **Bamboo** | `Bamboo` | Rendering culling, update frequency suppression (backward compatibility) |
| **IntelliInput** | `IntelliInput` | IME callback stabilization (backward compatibility) |
| **GTNHLib** | `GTNHLib` | Icon resolution, thread-safety |
| **Hodgepodge** | `Hodgepodge` | Avoids Guava class loader conflicts |
| **LiteLoader** | `LiteLoader` | Permission management fixes |
| **MacroMod** | `MacroMod` | Removal of `tamperCheck`, permission fixes |
| **Keybind Mod** | *(Bundled detection)* | Removal of `tamperCheck` |
| **RailMapCustom** | `RailMapCustom` | Rail map cache |

---

## 📥 Installation

1. Place the downloaded `CrossTie-*.jar` into the `mods` folder.
2. Place the required `UniMixins 0.3.1+` into the `mods` folder.
3. Depending on your needs, add target Mods such as RTM / Angelica / Bamboo / IntelliInput.
4. Start the game normally.

---

## 🛠️ Build and Development

### 🧱 Build Procedure
```bash
./gradlew build --no-daemon