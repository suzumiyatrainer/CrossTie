# 🛠️ CrossTie

面向 Minecraft 1.7.10 的 RTM (RealTrainMod) 系列综合优化与兼容性补丁 Mod。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 项目概要
CrossTie 将针对 RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod 等多个 Mod 的**减少渲染负载、抑制更新频率以及兼容性修正**整合在一个 JAR 文件中提供。

> 💡 **自动检测功能**: 目标 Mod 将在启动时被自动检测，仅在 Mod 存在时才会启用相应的补丁。

---

## 📊 状态
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=下载量%28最新版本%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 运行环境 & 构建状态
| 项目 | 状态 / 版本 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.0` |
| **Kotlin** | `2.1.0` |
| **必要 Mod** | `UniMixins 0.3.1+` |
| **构建系统** | RetroFuturaGradle 2.0.2 |
| **最后确认** | `2026-06-28` |

### 🔍 内部结构索引
* **Mixin 控制**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **可检测 Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 推荐配置

为了获得流畅运行，推荐的 Mod 版本配置如下。

| Mod 名称 | 推荐版本 | 类别 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **主体** |
| **UniMixins** | `0.3.1` | **必要** |
| **KaizPatchX** | `1.10.0` | 推荐 |
| **Angelica** | `2.1.42+` | 推荐 |
| **GTNHLib** | `0.11.18+` | 推荐 |
| **Hodgepodge** | `2.7.162+` | 可选 |
| **ArchaicFix** | `0.8.0+` | 可选 |
| **ShaderFixer** | `5.4+` | 可选 |

---

## ⚡ 本 Mod 的功能

CrossTie 旨在统一解决 RTM 相关 Mod 与性能优化类 Mod 之间产生的以下 **3 个核心问题**。

1. **🏃 FPS 优化**
   * [RTM] LargeRail 的距离剔除 (Culling) 与视锥体剔除
   * [RTM] LargeRail 的区块分批渲染
   * [RTM] LargeRail TESR 基于距离的渲染频率节流
   * [RTM] 轨道曲面细分 (Tessellation) 循环优化
   * [RTM] 电线与电线杆连接判定结果的缓存化
   * [RTM] 通过虚拟滚动技术大幅改善标志牌选择 GUI 的打开延迟
   * [RTM] 禁用信号灯/平交道的剔除以维持远景渲染
   * [NGTScriptUtil] 脚本执行 (Invocable) 缓存优化
   * [RailMapCustom] 铁路地图缓存优化
   * [MCTE] 世界方块差分集合优化
   * [Angelica] RenderGlobal.displayList 的原生优化
   * [KaizPatch, NGTScriptUtil, Angelica] 脚本 GL 调用重定向及缓存优化
2. **⏳ TPS / 服务器负载优化**
   * [RTM] 降低距离 256m 以上的 Train Entity 的客户端更新频率
   * [RTM] 优化 Train 速度 DataWatcher 同步以降低网络负载
   * [RTM] 缓存 Train onUpdate 中重复的 getBlock() 调用
   * [GTNHLib] 线程安全的对象池化
3. **🤝 兼容性与渲染 Bug 修正**
   * [Angelica] 修正启用着色器时原版云层重复渲染的问题
   * [Angelica] 修正启用着色器时水体渲染距离错误的问题
   * [Angelica, RTM] 修正方块重建时轨道 TESR 的光照不更新的问题
   * [Angelica] 修正启动画面的纹理状态缓存问题
   * [OptiFine, RTM] 修正 LargeRail 的 UV 坐标损坏（绿色竖线）问题
   * [OptiFine, RTM] 修正电线渲染时法线扭曲导致在着色器环境下完全透明的问题
   * [OptiFine, RTM] 修正 shadow pass 中电线无法渲染而消失的问题
   * [GTNHLib] 修正玻璃面板及方块的图标显示与获取回退机制
   * [Hodgepodge] 规避 Guava 类加载器冲突
   * [LiteLoader, MacroMod] 权限管理及核心兼容性修正
   * [MCTE] 修正微缩方块与微缩物品的动态光照
   * [KaizPatch] ModelLoaderKt 回退修正
4. **✨ 新功能**
   * [RTM] 增加免重启的重载模型包功能 (设置 或 mods→CrossTie→RTM→reloadPacks) *可能仍藏有少许 Bug，但大体运行正常。*
   * [RTM] 增加删除两点上方架空线（电线）的功能 (设定键 + 右键单击) *仅限空手时有效。*

---

## 🏗️ 架构

CrossTie 拥有 **3 层补丁机制**，在合适的阶段安全地进行干预。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
作为 `IFMLLoadingPlugin`，在 Minecraft 启动后的最早期阶段运行。
* **ModDetector**: 扫描 `mods/` 文件夹，通过 JAR/ZIP/litemod 的文件名自动检测已安装的 Mod。
* **MinFo 检测 + Angelica 设置自动调整**: 如果检测到 MinFo，将强制将 `config/angelica-modules.cfg` 中的 `B:enableFontRenderer` 修改为 `false`。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
在 Mixin 阶段之前，在类加载时直接修改字节码。这可以完全规避 Mixin 无法处理的“加载顺序问题”或 `MixinTargetAlreadyLoadedException`。

| 目标类 / 方法 | 内容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | 将 `nhlib$getParticleIcon` 重定向至 `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | 将玻璃面板图标解析 (如 `tweakPaneIcons`) 重定向至 `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | 将 `setupIcons` 替换为 `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | 将 `getString(s)` 替换为 `s.intern()` (规避 Guava 类加载器冲突) |
| **NGTLib/RTM** `ScriptUtil` | 将 `doScript(String)` 替换为 `ScriptUtilFallback.doScript(String)` (适配无 Nashorn 环境) |
| **MacroMod** `MacroModPermissions` | 删除所有方法中的 `tamperCheck()` 调用 |
| **LiteLoader** `PermissionsManagerClient` | 将 `tamperCheck()` 变为 no-op (无操作) |
| **SplashProgress$3** (`SplashProgress$3`) | 在 `run()` 开头通过反射注入 GL 状态重置 (`GL_TEXTURE_2D` + `glColor4f`) |

### 3. Mixin (动态应用)
`CrossTieMixinPlugin` 根据检测到的已安装 Mod 动态应用所需的 Mixin。

<details>
<summary>🔍 各 Mod 的 Mixin 应用详细列表（点击展开）</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * 渲染 `hi03ExpressRailwayRail` 时，避开 Angelica 的显示列表，使用旧版 OpenGL 路径。
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * 修正启动页面的纹理状态缓存问题。

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib 常驻)
    * 实现线程安全的对象池化。
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * 修正玻璃面板及方块的图标显示与获取回退机制。

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * 优化 Invocable 缓存。
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * 使用 `ScriptGlRedirector` 拦截 `AngelicaCompat.transformScript` (GL 调用重定向 + 缓存)。
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * 为 `ModelPackManager.getScript` 应用 `ScriptGlRedirector`。
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * 优化轨道地图缓存。
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * 优化世界方块差分集合。
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * 修正微缩方块与微缩物品的动态光照。
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * 优化列车速度同步及实体更新。
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * 线路渲染与电线杆连接的缓存化。
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * 大型轨道渲染优化与分块批处理。
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * 轨道零件渲染器优化。

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * 权限相关及核心兼容性修正。

</details>

---

## 📦 支持的 Mod 详情

| Mod | 检测名称 | 主要补丁/内容 |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | 渲染优化、更新抽稀、GL 重定向 |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil 兼容性、GL 重定向 |
| **MCTE** | `MCTE` | 微缩渲染修正、动态光照 |
| **KaizPatch** | `KaizPatch` | Angelica 联动、脚本缓存 |
| **Angelica** | `Angelica` / `AngelicaGlsm` | 显示列表冲突修正、设置自动调整 |
| **Bamboo** | `Bamboo` | 渲染剔除、抑制更新频率 (向下兼容) |
| **IntelliInput** | `IntelliInput` | IME 回调稳定化 (向下兼容) |
| **GTNHLib** | `GTNHLib` | 图标解析、线程安全化 |
| **Hodgepodge** | `Hodgepodge` | 规避 Guava 类加载器冲突 |
| **LiteLoader** | `LiteLoader` | 权限管理修正 |
| **MacroMod** | `MacroMod` | 移除 `tamperCheck`、权限修正 |
| **Keybind Mod** | *(同梱检测)* | 移除 `tamperCheck` |
| **RailMapCustom** | `RailMapCustom` | 轨道地图缓存 |

---

## 📥 安装方法

1. 将下载的 `CrossTie-*.jar` 放入 `mods` 文件夹。
2. 将必要的 `UniMixins 0.3.1+` 放入 `mods` 文件夹。
3. 根据需要，添加 RTM / Angelica / Bamboo / IntelliInput 等目标 Mod。
4. 正常启动游戏。

---

## 🛠️ 构建与开发

### 🧱 构建步骤
```bash
./gradlew build --no-daemon
```