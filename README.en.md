# 🛠️ CrossTie

A comprehensive optimization and compatibility patch Mod for RTM (RealTrainMod) related mods for Minecraft 1.7.10.

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 Project Overview
CrossTie provides **rendering load reduction, update frequency suppression, and compatibility fixes** across multiple mods including RTM / NGTLib / MCTE (KaizPatchX), Angelica, Bamboo, IntelliInput, GTNHLib, Hodgepodge, LiteLoader / Macro / Keybind Mod, all in a single JAR.

> 💡 **Auto-detection Feature**: Target mods are automatically detected upon launch, and the corresponding patches are enabled only when they are present.

---

## 📊 Status
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=Downloads%28latest%20release%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 Operating Environment & Build Status
| Item | Status / Version |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.0` |
| **Required Mod** | `UniMixins 0.3.1+` |
| **Build System** | RetroFuturaGradle 2.0.2 |
| **Last Checked** | `2026-06-29` |

### 🔍 Internal Structure Index
* **Mixin Control**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **Detectable Mods**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 Recommended Configuration

Recommended mod version configuration for comfortable operation.

| Mod Name | Recommended Version | Category |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **Core** |
| **UniMixins** | `0.3.1` | **Required** |
| **KaizPatchX** | `1.10.0` | Recommended |
| **Angelica** | `2.1.42+` | Recommended |
| **GTNHLib** | `0.11.18+` | Recommended |
| **Hodgepodge** | `2.7.162+` | Optional |
| **ArchaicFix** | `0.8.0+` | Optional |
| **ShaderFixer** | `5.4+` | Optional |

---

## ⚡ What Does This Mod Do?

CrossTie comprehensively solves the following **3 core problems** that occur between RTM-related mods and performance mods.

1. **🏃 FPS Optimization**
   * [RTM] Distance culling and frustum culling for LargeRail
   * [RTM] Chunk-based rendering aggregation for LargeRail
   * [RTM] Distance-based rendering frequency throttling for LargeRail TESR
   * [RTM] Rail tessellation loop optimization
   * [RTM] Caching of wiring and pole connection judgment results
   * [RTM] Improved opening delay of signboard selection GUI via virtual scrolling
   * [RTM] Maintenance of distant view rendering by disabling signal/crossing culling
   * [NGTScriptUtil] Script execution (Invocable) cache optimization
   * [RailMapCustom] Rail map cache optimization
   * [MCTE] World block diff set optimization
   * [Angelica] Native optimization of RenderGlobal.displayList
   * [KaizPatch, NGTScriptUtil, Angelica] Redirect and cache optimization of GL calls from scripts
2. **⏳ TPS / Server Load Optimization**
   * [RTM] Reduced client-side update frequency of Train Entities over 256m away
   * [RTM] Network load reduction through Train speed DataWatcher synchronization optimization
   * [RTM] Caching of duplicate getBlock() calls in Train onUpdate
   * [GTNHLib] Thread-safe object pooling
3. **🤝 Compatibility & Rendering Bug Fixes**
   * [Angelica] Fix for double rendering of vanilla clouds when shaders are enabled
   * [Angelica] Fix for incorrect water rendering distance when shaders are enabled
   * [Angelica, RTM] Fix for rail TESR lighting not updating upon block rebuild
   * [Angelica] Fix for splash screen texture state caching issue
   * [OptiFine, RTM] Fix for LargeRail UV coordinate destruction (vertical green lines)
   * [OptiFine, RTM] Fix for transparency in shader environments due to normal distortion when rendering wires
   * [OptiFine, RTM] Fix for wires disappearing (not rendering) during shadow pass
   * [GTNHLib] Glass pane and block icon display/retrieval fallback fix
   * [Hodgepodge] Avoidance of Guava classloader conflicts
   * [LiteLoader, MacroMod] Permission management and core compatibility fixes
   * [MCTE] Dynamic lighting fix for miniature blocks and item miniatures
   * [KaizPatch] ModelLoaderKt fallback fix
4. **✨ Addition of New Features**
   * [RTM] Added model pack reloading feature without restarting (Settings or mods -> CrossTie -> RTM -> reloadPacks) *May still contain bugs, but generally works normally.*
   * [RTM] Feature to delete the overhead wire 2 points above (Configured Key + Right Click) *Does not work unless empty-handed.*
   * [RTM] Added Sound API for in-car announcements that doesn't leak outside when doors are closed

---

## 🏗️ Architecture

CrossTie has a **3-layer patch mechanism** that intervenes safely in the appropriate phases.

### 1. ASM CoreMod (`CrossTieCorePlugin`)
Operates as an `IFMLLoadingPlugin` in the earliest phase right after Minecraft starts.
* **ModDetector**: Scans the `mods/` folder and automatically detects installed mods from JAR/ZIP/litemod filenames.
* **MinFo Detection + Angelica Settings Auto-adjustment**: If MinFo is detected, forcibly rewrites `B:enableFontRenderer` to `false` in `config/angelica-modules.cfg`.

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
Directly rewrites bytecode upon class loading before the Mixin phase. Completely avoids "load order issues" and "MixinTargetAlreadyLoadedException" that Mixins cannot handle.

| Target Class / Method | Content |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | Redirects `nhlib$getParticleIcon` to `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | Redirects glass pane icon resolution like `tweakPaneIcons` to `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | Replaces `setupIcons` with `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | Replaces `getString(s)` -> `s.intern()` (Avoids Guava classloader conflict) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` -> `ScriptUtilFallback.doScript(String)` (Support for environments without Nashorn) |
| **MacroMod** `MacroModPermissions` | Removes `tamperCheck()` calls from all methods |
| **LiteLoader** `PermissionsManagerClient` | `tamperCheck()` -> no-op |
| **SplashProgress$3** (`SplashProgress$3`) | Injects GL state reset (`GL_TEXTURE_2D` + `glColor4f`) via reflection at the start of `run()` |

### 3. Mixin (Dynamic Application)
`CrossTieMixinPlugin` dynamically applies only the necessary Mixins based on the detected installed mods.

<details>
<summary>🔍 Mixin Application Details for Each Mod (Click to expand)</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * When rendering `hi03ExpressRailwayRail`, bypasses Angelica's display list and uses the old OpenGL path
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * Fixes the splash screen texture state caching issue

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib Always)
    * Thread-safe object pooling
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * Glass pane and block icon display/retrieval fallback fix

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * Invocable cache optimization
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * Intercepts `AngelicaCompat.transformScript` with `ScriptGlRedirector` (GL call redirect + caching)
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * Applies `ScriptGlRedirector` to `ModelPackManager.getScript`
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * Rail map cache optimization
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * World block diff set optimization
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * Dynamic lighting fix for miniature blocks/item miniatures
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * Train speed synchronization and entity update optimization
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * Wiring rendering/rail pole connection caching
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * Large rail rendering optimization/chunk batch processing
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * Rail parts renderer optimization

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * Permission management and core compatibility fixes

</details>

---

## 📦 Supported Mod Details

| Mod | Detection Name | Main Patches / Support Details |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | Rendering optimization, update thinning, GL redirect |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil compatibility, GL redirect |
| **MCTE** | `MCTE` | Miniature rendering fix, dynamic lighting |
| **KaizPatch** | `KaizPatch` | Angelica integration, script cache |
| **Angelica** | `Angelica` / `AngelicaGlsm` | Display list conflict fix, settings auto-adjustment |
| **Bamboo** | `Bamboo` | Rendering culling, update frequency suppression (backward compatibility) |
| **IntelliInput** | `IntelliInput` | IME callback stabilization (backward compatibility) |
| **GTNHLib** | `GTNHLib` | Icon resolution, thread-safety |
| **Hodgepodge** | `Hodgepodge` | Guava classloader conflict avoidance |
| **LiteLoader** | `LiteLoader` | Permission management fix |
| **MacroMod** | `MacroMod` | `tamperCheck` removal, permission fix |
| **Keybind Mod** | *(Bundled Detection)* | `tamperCheck` removal |
| **RailMapCustom** | `RailMapCustom` | Rail map cache |

---

## 📥 Installation

1. Place the downloaded `CrossTie-*.jar` into the `mods` folder.
2. Place the required `UniMixins 0.3.1+` into the `mods` folder.
3. Add target mods such as RTM / Angelica / Bamboo / IntelliInput as needed.
4. Launch the game normally.

---

## 🛠️ Build and Development

### 🧱 Build Instructions
```bash
./gradlew build --no-daemon
```