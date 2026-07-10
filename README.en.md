# 🛠️ CrossTie

A comprehensive optimization and compatibility patch mod for RTM (RealTrainMod) related mods on Minecraft 1.7.10.

> ⚠️ **Build & Development Notice**:
> `src/main/java/jp/kaiz/atsassistmod/block/tileentity/TileEntityIFTTT.java` is a **compile-only dummy class (stub)** used to prevent compilation errors in CI environments (such as GitHub Actions).
> Since it is automatically excluded from the final production JAR during the build process, it does not affect game execution.

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 Project Overview
CrossTie provides **rendering load reduction, update frequency suppression, and compatibility fixes** across multiple mods such as RTM / NGTLib / MCTE (KaizPatchX), Angelica, Bamboo, IntelliInput, GTNHLib, Hodgepodge, LiteLoader / Macro / Keybind Mod, WorldEdit, ProjectRed, CustomNPC+, and more — all in a single JAR.

> 💡 **Auto-Detection Feature**: Target mods are automatically detected at launch, and the corresponding patches are enabled only if they exist.

---

## 📊 Status
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=Downloads%28latest%20release%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 Environment & Build Status
| Item | Status / Version |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.1` |
| **Required Mod** | `UniMixins 0.3.1+` |
| **Build System** | RetroFuturaGradle 2.0.2 |
| **Last Checked** | `2026-07-10` |

### 🔍 Internal Structure Index
* **Mixin Control**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **Late Mixin Control**: [`CrossTieLateMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieLateMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **Detectable Mods**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`, `WorldEdit`, `ProjectRed`, `CustomNPC+`, `ATSAssist`, `SignPicture`, `ArchitectureCraft`

---

## 🚀 Recommended Configuration

Recommended mod versions for a smooth experience.

| Mod Name | Recommended Version | Category |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **Core** |
| **UniMixins** | `0.3.1` | **Required** |
| **KaizPatchX** | `1.10.0` | Recommended |
| **Angelica** | `2.1.51` | Recommended |
| **GTNHLib** | `0.11.23+` | Recommended |
| **Hodgepodge** | `2.7.171+` | Optional |
| **ArchaicFix** | `0.8.0+` | Optional |
| **ShaderFixer** | `5.4+` | Optional |

---

## ⚡ What Does This Mod Do?

CrossTie collectively solves and provides the following **4 core features** regarding issues between RTM-related mods and performance mods.

1. **🏃 FPS Optimization**
   * LargeRail rendering optimization, script execution optimization, display list optimization, etc.
   * Details: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

2. **⏳ TPS / Server Load Optimization**
   * Train Entity update frequency optimization, network load reduction, object pool optimization, etc.
   * Details: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

3. **🤝 Compatibility & Rendering Bug Fixes**
   * RTM rendering bug fixes under Angelica or OptiFine, and compatibility fixes for other surrounding mods.
   * Details:
     * [`doc/Angelica・GTNHLib関連互換性修正.md`](./doc/Angelica・GTNHLib関連互換性修正.md)
     * [`doc/OptiFine・FastCraft関連互換性修正.md`](./doc/OptiFine・FastCraft関連互換性修正.md)
     * [`doc/RTM・NGTLib関連_バグ修正.md`](./doc/RTM・NGTLib関連_バグ修正.md)
     * [`doc/その他周辺Mod互換性修正.md`](./doc/その他周辺Mod互換性修正.md)

4. **✨ New Features**
   * Hot-reload of model packs without restart, wire removal feature, and sound API for in-car announcements, etc.
   * Details: [`doc/新規機能の使い方/`](./doc/新規機能の使い方/)

---

## 🏗️ Architecture

CrossTie has a **3-layer patch mechanism** that safely intervenes at appropriate phases.

### 1. ASM CoreMod (`CrossTieCorePlugin`)
Runs in the very first phase immediately after Minecraft starts as an `IFMLLoadingPlugin`.
* **ModDetector**: Scans the `mods/` folder and automatically detects installed mods from JAR/ZIP/litemod filenames.
* **MinFo Detection + Angelica Settings Auto-Adjustment**: If MinFo is detected, forcibly rewrites `B:enableFontRenderer` in `config/angelica-modules.cfg` to `false`.

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
Directly rewrites bytecodes at class loading, before the Mixin phase. It completely avoids "load order issues" and "MixinTargetAlreadyLoadedException" that Mixins cannot handle.

| Target Class / Method | Action |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | Redirects `nhlib$getParticleIcon` to `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | Redirects glass pane icon resolution like `tweakPaneIcons` to `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | Replaces `setupIcons` with `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | Replaces `getString(s)` with `s.intern()` to avoid Guava classloader conflicts |
| **NGTLib/RTM** `ScriptUtil` | Replaces `doScript(String)` with `ScriptUtilFallback.doScript(String)` (for environments without Nashorn) |
| **MacroMod** `MacroModPermissions` | Removes `tamperCheck()` calls from all methods |
| **LiteLoader** `PermissionsManagerClient` | Makes `tamperCheck()` a no-op |
| **SplashProgress$3** (`SplashProgress$3`) | Injects GL state reset (`GL_TEXTURE_2D` + `glColor4f`) via reflection at the beginning of `run()` |

### 3. Mixin (Dynamic Application)
`CrossTieMixinPlugin` dynamically applies only the necessary Mixins based on the detected installed mods.
To avoid early classloading issues, some Mixins are applied via deferred loading through `CrossTieLateMixinPlugin` (e.g., ProjectRed).

Please refer to the documents in the `doc/` directory for detailed patch contents.

---

## 📥 Installation

1. Place the downloaded `CrossTie-*.jar` in your `mods` folder.
2. Place the required `UniMixins 0.3.1+` in your `mods` folder.
3. Add target mods like RTM / Angelica / Bamboo / IntelliInput depending on your purpose.
4. Launch the game as usual.

---

## 🛠️ Build and Development

This project uses **RetroFuturaGradle (RFG)** as the build system.

### 🧱 Build Instructions
Standard build procedure. The compiled `.jar` file will be generated in `build/libs/`.
```bash
./gradlew build --no-daemon
```

### 💻 Development Environment Setup

#### IntelliJ IDEA
1. Run the following command in a command prompt to generate project files for IDEA.
```bash
./gradlew idea
```
2. Open the folder in IntelliJ IDEA and import the project.

#### Eclipse
1. Run the following command to generate project files for Eclipse.
```bash
./gradlew eclipse
```
2. Import the project in Eclipse via "Import existing projects into workspace".

### ▶️ Running in the Development Environment
You can start the test client by using the automatically generated Run Configuration in the IDE, or by running the following command:
```bash
./gradlew runClient
```