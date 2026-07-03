# 🛠️ CrossTie

面向 Minecraft 1.7.10 的 RTM (RealTrainMod) 系综合优化与兼容性补丁 Mod。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 项目简介
CrossTie 将跨越多个 Mod 的**渲染减负、更新频率抑制、兼容性修复**集中在单个 JAR 中提供。支持的 Mod 包括 RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod 等。

> 💡 **自动检测功能**: 目标 Mod 将在启动时被自动检测，仅在存在时才启用相应的补丁。

---

## 📊 状态
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=下载量%28最新发布%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 运行环境 & 构建状态
| 项目 | 状态 / 版本 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.0` |
| **前置 Mod** | `UniMixins 0.3.1+` |
| **构建系统** | RetroFuturaGradle 2.0.2 |
| **最后确认** | `2026-06-29` |

### 🔍 内部结构索引
* **Mixin 控制**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **可检测 Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 推荐配置

为流畅运行而推荐的 Mod 版本配置。

| Mod 名称 | 推荐版本 | 类别 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **本体** |
| **UniMixins** | `0.3.1` | **必装** |
| **KaizPatchX** | `1.10.0` | 推荐 |
| **Angelica** | `2.1.42+` | 推荐 |
| **GTNHLib** | `0.11.18+` | 推荐 |
| **Hodgepodge** | `2.7.162+` | 可选 |
| **ArchaicFix** | `0.8.0+` | 可选 |
| **ShaderFixer** | `5.4+` | 可选 |

---

## ⚡ 本 Mod 的作用

CrossTie 综合解决了 RTM 相关 Mod 群与性能优化 Mod 群之间发生的以下 **3个核心问题**。

1. **🏃 FPS 优化**
   * [RTM] 大型铁轨 (LargeRail) 的距离剔除与视锥体剔除
   * [RTM] 大型铁轨的区块单位渲染聚合
   * [RTM] 大型铁轨 TESR 根据距离的渲染频率节流
   * [RTM] 铁轨细分曲面循环优化
   * [RTM] 电线及支柱连接判定结果缓存
   * [RTM] 招牌选择 GUI 虚拟滚动化，改善打开延迟
   * [RTM] 信号灯/平交道口取消剔除，维持远景渲染
   * [NGTScriptUtil] 脚本执行 (Invocable) 的缓存优化
   * [RailMapCustom] 铁路地图缓存优化
   * [MCTE] 世界方块差异集合优化
   * [Angelica] RenderGlobal.displayList 的原生优化
   * [KaizPatch, NGTScriptUtil, Angelica] 脚本发出的 GL 调用重定向及缓存优化
2. **⏳ TPS / 服务器负载优化**
   * [RTM] 降低距离 256m 以上列车实体的客户端更新频率
   * [RTM] 优化列车速度的 DataWatcher 同步以降低网络负载
   * [RTM] 缓存列车 onUpdate 内重复的 getBlock() 调用
   * [GTNHLib] 线程安全的对象池化
3. **🤝 兼容性与渲染错误修复**
   * [Angelica] 修复开启光影时原版云层重复渲染的问题
   * [Angelica] 修复开启光影时水面渲染距离异常的问题
   * [Angelica, RTM] 修复区块重建时铁轨 TESR 光照不更新的问题
   * [Angelica] 修复加载界面的纹理状态缓存问题
   * [OptiFine, RTM] 修复大型铁轨 UV 坐标损坏（绿色竖线）的问题
   * [OptiFine, RTM] 修复电线渲染时法线扭曲导致在光影环境下透明化的问题
   * [OptiFine, RTM] 修复阴影通道(shadow pass)中电线不渲染而消失的问题
   * [GTNHLib] 修复玻璃板及方块图标显示与获取失败的回退
   * [Hodgepodge] 避免 Guava 类加载器冲突
   * [LiteLoader, MacroMod] 权限管理及核心的兼容性修复
   * [MCTE] 修复微缩方块及物品微缩的动态光照
   * [KaizPatch] ModelLoaderKt 回退修复
4. **✨ 新功能追加**
   * [RTM] 添加无需重启的重新加载模型包功能（在配置或 mods→CrossTie→RTM→reloadPacks）※可能存在隐患，但大体可正常工作。
   * [RTM] 能够删除头顶上方两格处的架线（设置热键+右键）※手中不能拿物品。
   * [RTM] 添加在关门时不会传到车外的车内广播音频 API

---

## 🏗️ 架构

CrossTie 拥有**三层补丁机制**，在适当阶段安全介入。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
作为 `IFMLLoadingPlugin`，在 Minecraft 启动后最早阶段运行。
* **ModDetector**: 扫描 `mods/` 文件夹，从 JAR/ZIP/litemod 文件名自动检测安装的 Mod。
* **MinFo 检测 + Angelica 设置自动调整**: 如果检测到 MinFo，强制将 `config/angelica-modules.cfg` 中的 `B:enableFontRenderer` 改写为 `false`。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
在类加载时，于 Mixin 阶段之前直接修改字节码。完全避免了 Mixin 无法处理的“加载顺序问题”以及“MixinTargetAlreadyLoadedException”。

| 目标类 / 方法 | 内容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | 将 `nhlib$getParticleIcon` 重定向至 `GtnhLibIconCompat` |
| **Angelica CTM** `MixinRenderBlocks` | 将 `tweakPaneIcons` 等玻璃板图标解析重定向至 `AngelicaPaneIconCompat` |
| **MCPatcher** `GlassPaneRenderer` | 将 `setupIcons` 替换为 `return false` |
| **Hodgepodge** `StringPooler$GuavaPooler` | 将 `getString(s)` → 替换为 `s.intern()` (避免 Guava 类加载器冲突) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)` (支持无 Nashorn 环境) |
| **MacroMod** `MacroModPermissions` | 移除所有方法中的 `tamperCheck()` 调用 |
| **LiteLoader** `PermissionsManagerClient` | `tamperCheck()` → 设为 no-op 空操作 |
| **SplashProgress$3** (`SplashProgress$3`) | 在 `run()` 头部注入通过反射的 GL 状态重置 (`GL_TEXTURE_2D` + `glColor4f`) |

### 3. Mixin (动态应用)
`CrossTieMixinPlugin` 根据检测到的已安装 Mod 动态应用必要的 Mixin。

<details>
<summary>🔍 各 Mod 的 Mixin 应用详情列表（点击展开）</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * 在渲染 `hi03ExpressRailwayRail` 时，绕过 Angelica 的显示列表并使用旧的 OpenGL 路径
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * 修复加载界面纹理状态缓存问题

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib 始终)
    * 线程安全的对象池化
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * 修复玻璃板及方块图标显示与获取回退

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * 优化 Invocable 缓存
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * 使用 `ScriptGlRedirector` 拦截 `AngelicaCompat.transformScript` (GL 调用重定向 + 缓存)
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * 向 `ModelPackManager.getScript` 应用 `ScriptGlRedirector`
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * 铁路地图缓存优化
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * 世界方块差异集合优化
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * 修复微缩方块/物品微缩的动态光照
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * 列车速度同步及实体更新优化
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * 优化电线渲染及铁轨立柱连接缓存
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * 大型铁轨渲染优化及区块批量处理
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * 铁轨零件渲染器优化

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * 权限管理及核心兼容性修复

</details>

---

## 📦 支持 Mod 详情

| Mod | 检测名称 | 主要补丁・对应内容 |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | 渲染优化，减少更新，GL 重定向 |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil 兼容，GL 重定向 |
| **MCTE** | `MCTE` | 微缩渲染修复，动态光照 |
| **KaizPatch** | `KaizPatch` | 联动 Angelica，脚本缓存 |
| **Angelica** | `Angelica` / `AngelicaGlsm` | 显示列表冲突修复，设置自动调整 |
| **Bamboo** | `Bamboo` | 渲染剔除，降低更新频率（向后兼容） |
| **IntelliInput** | `IntelliInput` | IME 回调稳定化（向后兼容） |
| **GTNHLib** | `GTNHLib` | 图标解析，线程安全化 |
| **Hodgepodge** | `Hodgepodge` | 避免 Guava 类加载器冲突 |
| **LiteLoader** | `LiteLoader` | 权限管理修复 |
| **MacroMod** | `MacroMod` | 移除 `tamperCheck`，权限修复 |
| **Keybind Mod** | *(随附检测)* | 移除 `tamperCheck` |
| **RailMapCustom** | `RailMapCustom` | 铁路地图缓存 |

---

## 📥 安装方法

1. 将下载的 `CrossTie-*.jar` 放入 `mods` 文件夹中。
2. 将前置 `UniMixins 0.3.1+` 放入 `mods` 文件夹中。
3. 根据需要添加 RTM / Angelica / Bamboo / IntelliInput 等对象 Mod。
4. 像往常一样启动游戏即可。

---

## 🛠️ 构建与开发

### 🧱 构建步骤
```bash
./gradlew build --no-daemon
```