# 🛠️ CrossTie

面向 Minecraft 1.7.10 的 RTM (RealTrainMod) 相关模组综合优化与兼容性补丁模组。

> ⚠️ **构建与开发注意事项**:
> `src/main/java/jp/kaiz/atsassistmod/block/tileentity/TileEntityIFTTT.java` 是一个**仅用于编译的虚构类（Stub）**，用于防止在 CI 环境（如 GitHub Actions 等）下发生编译错误。
> 该类在构建最终发布 JAR 文件时会自动被排除（exclude），因此不会影响游戏运行时的行为。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 项目简介
CrossTie 将跨越多个模组（如 RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod、WorldEdit、ProjectRed、CustomNPC+ 等）的**渲染负载降低、更新频率抑制与兼容性修复**功能集成在一个 JAR 文件中提供。

> 💡 **自动检测功能**: 启动时会自动检测目标模组，仅在模组存在时启用对应的补丁。

---

## 📊 状态
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=%E4%B8%8B%E8%BD%BD%E9%87%8F%28%E6%9C%80%E6%96%B0%E5%8F%91%E5%B8%83%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 运行环境与构建状态
| 项目 | 状态 / 版本 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.1` |
| **前置模组** | `UniMixins 0.3.1+` |
| **构建系统** | RetroFuturaGradle 2.0.2 |
| **最后确认** | `2026-07-10` |

### 🔍 内部结构索引
* **Mixin 控制**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **延迟 Mixin 控制**: [`CrossTieLateMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieLateMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **可检测模组**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`, `WorldEdit`, `ProjectRed`, `CustomNPC+`, `ATSAssist`, `SignPicture`, `ArchitectureCraft`

---

## 🚀 推荐配置

为保证流畅运行推荐的模组版本配置。

| 模组名称 | 推荐版本 | 类别 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **核心** |
| **UniMixins** | `0.3.1` | **必装** |
| **KaizPatchX** | `1.10.0` | 推荐 |
| **Angelica** | `2.1.49` | 推荐 |
| **GTNHLib** | `0.11.23+` | 推荐 |
| **Hodgepodge** | `2.7.171+` | 可选 |
| **ArchaicFix** | `0.8.0+` | 可选 |
| **ShaderFixer** | `5.4+` | 可选 |

---

## ⚡ 本模组的功能

CrossTie 汇总解决了 RTM 相关模组与性能优化模组之间发生的以下 **4 项核心内容**。

1. **🏃 FPS 优化**
   * LargeRail 渲染优化、脚本执行优化、显示列表（Display List）优化等。
   * 详情: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

2. **⏳ TPS / 服务器负载优化**
   * 列车实体更新频率优化、网络负载降低、对象池优化等。
   * 详情: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

3. **🤝 兼容性与渲染错误修复**
   * 修复在 Angelica 或 OptiFine 环境下的 RTM 渲染错误，以及其他周边模组的冲突。
   * 详情:
     * [`doc/Angelica・GTNHLib関連互換性修正.md`](./doc/Angelica・GTNHLib関連互換性修正.md)
     * [`doc/OptiFine・FastCraft関連互換性修正.md`](./doc/OptiFine・FastCraft関連互換性修正.md)
     * [`doc/RTM・NGTLib関連_バグ修正.md`](./doc/RTM・NGTLib関連_バグ修正.md)
     * [`doc/その他周辺Mod互換性修正.md`](./doc/その他周辺Mod互換性修正.md)

4. **✨ 新功能添加**
   * 免重启重新加载模型包功能、架空线删除功能、车厢内广播用音效 API 等。
   * 详情: [`doc/新規機能の使い方/`](./doc/新規機能の使い方/)

---

## 🏗️ 架构

CrossTie 具有 **3 层补丁机制**，在合适的阶段安全地介入。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
作为 `IFMLLoadingPlugin`，在 Minecraft 启动后的最初阶段运行。
* **ModDetector**: 扫描 `mods/` 文件夹，根据 JAR/ZIP/litemod 的文件名自动检测已安装的模组。
* **MinFo 检测与 Angelica 设置自动调整**: 如果检测到 MinFo，则强制将 `config/angelica-modules.cfg` 中的 `B:enableFontRenderer` 改写为 `false`。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
在类加载时，早于 Mixin 阶段直接修改字节码。完全避免了 Mixin 无法处理的"加载顺序问题"和"MixinTargetAlreadyLoadedException"。

| 目标类 / 方法 | 内容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | 将 `nhlib$getParticleIcon` 重定向到 `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | 将 `tweakPaneIcons` 等玻璃板图标解析重定向到 `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | 将 `setupIcons` 替换为 `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | 将 `getString(s)` 替换为 `s.intern()`（避免 Guava 类加载器冲突） |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)`（适配缺失 Nashorn 的环境） |

### 3. Mixin (动态应用)
`CrossTieMixinPlugin` 会根据检测到的已安装模组，动态应用所需的 Mixin。
为防止早期类加载冲突，部分 Mixin 通过 `CrossTieLateMixinPlugin` 进行延迟加载（例如：ProjectRed）。

详细的补丁内容，请参阅 `doc/` 目录下的各文档。

---

## 📥 安装方法

1. 将下载的 `CrossTie-*.jar` 放入 `mods` 文件夹。
2. 将必装前置 `UniMixins 0.3.1+` 放入 `mods` 文件夹。
3. 根据需要，添加 RTM / Angelica / Bamboo / IntelliInput 等目标模组。
4. 像往常一样启动游戏。

---

## 🛠️ 构建与开发

本项目使用 **RetroFuturaGradle (RFG)** 作为构建系统。

### 🧱 构建步骤
标准构建步骤。编译后的 `.jar` 文件将生成在 `build/libs/` 目录下。
```bash
./gradlew build --no-daemon
```

### 💻 开发环境设置

#### IntelliJ IDEA
1. 在命令提示符等中执行以下命令，生成 IDEA 用的项目文件。
```bash
./gradlew idea
```
2. 在 IntelliJ IDEA 中打开文件夹并导入项目。

#### Eclipse
1. 执行以下命令，生成 Eclipse 用的项目文件。
```bash
./gradlew eclipse
```
2. 在 Eclipse 中通过"将现有项目导入到工作空间"导入项目。

### ▶️ 在开发环境中运行
你可以使用 IDE 自动生成的运行配置（Run Configuration），或通过以下命令启动测试客户端：
```bash
./gradlew runClient
```