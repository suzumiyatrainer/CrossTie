# 🛠️ CrossTie

Minecraft 1.7.10 向けの RTM（RealTrainMod）系総合最適化・互換パッチ Mod です。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 プロジェクト概要
CrossTie は、RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod など、複数 Mod にわたる**描画負荷削減・更新頻度抑制・互換性修正**を 1 つの JAR で提供します。

> 💡 **自動検出機能**: 対象 Mod は起動時に自動検出され、存在する場合にのみ対応するパッチが有効化されます。

---

## 📊 ステータス
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=ダウンロード数%28最新のリリース%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 動作環境 & ビルドステータス
| 項目 | 状態 / バージョン |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.0` |
| **Kotlin** | `2.1.0` |
| **必須Mod** | `UniMixins 0.3.1+` |
| **ビルドシステム** | RetroFuturaGradle 2.0.2 |
| **最終確認** | `2026-06-28` |

### 🔍 内部構造インデックス
* **Mixin 制御**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **検出可能Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 おすすめ構成

快適に動作させるための推奨Modバージョン構成です。

| Mod名 | 推奨バージョン | 区分 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **本体** |
| **UniMixins** | `0.3.1` | **必須** |
| **KaizPatchX** | `1.10.0` | 推奨 |
| **Angelica** | `2.1.42+` | 推奨 |
| **GTNHLib** | `0.11.18+` | 推奨 |
| **Hodgepodge** | `2.7.162+` | 任意 |
| **ArchaicFix** | `0.8.0+` | 任意 |
| **ShaderFixer** | `5.4+` | 任意 |

---

## ⚡ 何をする Mod か

CrossTie は、RTM 関連の Mod 群とパフォーマンス系 Mod 群の間で発生する、以下の **3つのコア問題**をまとめて解決します。

1. **🏃 FPS 最適化**
   * [RTM] LargeRailの距離カリングとフラストラムカリング
   * [RTM] LargeRailのチャンク単位レンダリング集約
   * [RTM] LargeRail TESRの距離に応じた描画頻度スロットル
   * [RTM] レールテッセレーションループの最適化
   * [RTM] 配線および支柱の接続判定結果のキャッシュ化
   * [RTM] サインボード選択GUIの仮想スクロール化による開き遅延改善
   * [RTM] シグナル/踏切のカリング無効化による遠景描画の維持
   * [NGTScriptUtil] スクリプト実行 (Invocable) のキャッシュ最適化
   * [RailMapCustom] レールマップのキャッシュ最適化
   * [MCTE] ワールドブロック差分セットの最適化
   * [Angelica] RenderGlobal.displayList のネイティブ最適化
   * [KaizPatch, NGTScriptUtil, Angelica] スクリプトからのGL呼び出しリダイレクトおよびキャッシュ最適化
2. **⏳ TPS / サーバー負荷最適化**
   * [RTM] 256m以上離れたTrain Entityのクライアント側更新頻度を低減
   * [RTM] Train速度の DataWatcher 同期最適化によるネットワーク負荷低減
   * [RTM] Train onUpdate 内の重複 getBlock() 呼び出しのキャッシュ化
   * [GTNHLib] スレッドセーフなオブジェクトプール化
3. **🤝 互換性・描画バグ修正**
   * [Angelica] シェーダー有効時にバニラ雲が二重描画される問題の修正
   * [Angelica] シェーダー有効時に水の描画距離が不正になる問題の修正
   * [Angelica, RTM] ブロックリビルド時にレールTESRのライティングが更新されない問題の修正
   * [Angelica] スプラッシュ画面のテクスチャ状態キャッシュ問題の修正
   * [OptiFine, RTM] LargeRailのUV座標破壊（緑の縦線）の修正
   * [OptiFine, RTM] ワイヤー描画時の法線の歪みによるシェーダー環境での透明化の修正
   * [OptiFine, RTM] shadow pass中にワイヤーが描画されず消えてしまう問題の修正
   * [GTNHLib] Glass pane およびブロックのアイコン表示・取得フォールバック修正
   * [Hodgepodge] Guava クラスローダーの競合回避
   * [LiteLoader, MacroMod] パーミッション管理およびコアの互換性修正
   * [MCTE] ミニチュアブロックおよびアイテムミニチュアの動的ライティング修正
   * [KaizPatch] ModelLoaderKt のフォールバック修正
4. **✨ 新機能の追加**
   * [RTM] 再起動不要のモデルパック再読み込み機能の追加 (設定 or mods→CrossTie→RTM→reloadPacks) ※バグが紛れてる可能性が未だありますが、概ね正常に動きます。
   * [RTM] 2点上の架線を削除する機能(キー設定で設定したキー+右クリック) ※素手でない場合は動きません。

---

## 🏗️ アーキテクチャ

CrossTie は **3層のパッチ機構** を持ち、適切なフェーズで安全に介入します。

### 1. ASM CoreMod (`CrossTieCorePlugin`)
`IFMLLoadingPlugin` として Minecraft 起動直後の最先発フェーズで動作します。
* **ModDetector**: `mods/` フォルダをスキャンし、JAR/ZIP/litemod のファイル名から導入Modを自動検出。
* **MinFo 検出 + Angelica 設定自動調整**: MinFo が検出された場合、`config/angelica-modules.cfg` の `B:enableFontRenderer` を強制的に `false` に書き換えます。

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
Mixin フェーズより前にクラスロード時にバイトコードを直接書き換えます。Mixin では対応できない「ロード順の問題」や「MixinTargetAlreadyLoadedException」を完全に回避します。

| 対象クラス / メソッド | 内容 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | `nhlib$getParticleIcon` を `GtnhLibIconCompat` にリダイレクト |
| **Angelica CTM** `MixinRenderBlocks` | `tweakPaneIcons` 等の glass pane アイコン解決を `AngelicaPaneIconCompat` にリダイレクト |
| **MCPatcher** `GlassPaneRenderer` | `setupIcons` を `return false` に置換 |
| **Hodgepodge** `StringPooler$GuavaPooler` | `getString(s)` → `s.intern()` に置換 (Guava クラスローダー競合回避) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)` (Nashorn 不在環境対応) |
| **MacroMod** `MacroModPermissions` | 全メソッドから `tamperCheck()` 呼び出しを削除 |
| **LiteLoader** `PermissionsManagerClient` | `tamperCheck()` → no-op 化 |
| **SplashProgress$3** (`SplashProgress$3`) | `run()` 先頭にリフレクション経由の GL 状態リセット (`GL_TEXTURE_2D` + `glColor4f`) を注入 |

### 3. Mixin (動的適用)
`CrossTieMixinPlugin` が、検出された導入Modに応じて必要な Mixin のみを動的に適用します。

<details>
<summary>🔍 各Modへの Mixin 適用詳細リスト（クリックで展開）</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * `hi03ExpressRailwayRail` 描画時、Angelica のディスプレイリストを回避し OpenGL 旧経路を使用
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * スプラッシュ画面のテクスチャ状態キャッシュ問題を修正

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib 常時)
    * スレッドセーフなオブジェクトプール化
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * Glass pane およびブロックのアイコン表示・取得フォールバック修正

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * Invocable キャッシュの最適化
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * `AngelicaCompat.transformScript` を `ScriptGlRedirector` でインターセプト (GL呼び出しのリダイレクト + キャッシュ)
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * `ModelPackManager.getScript` に `ScriptGlRedirector` を適用
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * レールマップのキャッシュ最適化
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * ワールドブロック差分セットの最適化
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * ミニチュアブロック・アイテムミニチュアの動的ライティング修正
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * 列車速度同期およびエンティティ更新の最適化
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * 配線レンダリング・線路柱接続のキャッシュ化
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * 大型レール描画の最適化・チャンクバッチ処理
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * レールパーツレンダラーの最適化

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * パーミッション周りおよびコアの互換性修正

</details>

---

## 📦 対応 Mod 詳細

| Mod | 検出名 | 主なパッチ・対応内容 |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | 描画最適化、更新間引き、GL リダイレクト |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil 互換、GL リダイレクト |
| **MCTE** | `MCTE` | ミニチュア描画修正、動的ライティング |
| **KaizPatch** | `KaizPatch` | Angelica 連携、スクリプトキャッシュ |
| **Angelica** | `Angelica` / `AngelicaGlsm` | ディスプレイリスト競合修正、設定自動調整 |
| **Bamboo** | `Bamboo` | 描画カリング、更新頻度抑制 (後方互換) |
| **IntelliInput** | `IntelliInput` | IME コールバック安定化 (後方互換) |
| **GTNHLib** | `GTNHLib` | アイコン解決、スレッドセーフ化 |
| **Hodgepodge** | `Hodgepodge` | Guava クラスローダー競合回避 |
| **LiteLoader** | `LiteLoader` | パーミッション管理修正 |
| **MacroMod** | `MacroMod` | `tamperCheck` 除去、パーミッション修正 |
| **Keybind Mod** | *(同梱検出)* | `tamperCheck` 除去 |
| **RailMapCustom** | `RailMapCustom` | レールマップキャッシュ |

---

## 📥 導入方法

1. ダウンロードした `CrossTie-*.jar` を `mods` フォルダに配置します。
2. 必須となる `UniMixins 0.3.1+` を `mods` フォルダに配置します。
3. 目的に応じて、RTM / Angelica / Bamboo / IntelliInput などの対象Modを追加します。
4. 通常通りゲームを起動します。

---

## 🛠️ ビルドと開発

### 🧱 ビルド手順
```bash
./gradlew build --no-daemon
```