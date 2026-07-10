# 🛠️ CrossTie

Minecraft 1.7.10 向けの RTM（RealTrainMod）系総合最適化・互換パッチ Mod です。

> ⚠️ **ビルド・開発時の注意**:
> `src/main/java/jp/kaiz/atsassistmod/block/tileentity/TileEntityIFTTT.java` は、CI環境（GitHub Actions等）でのコンパイル時エラーを防止するための**コンパイル専用ダミークラス（スタブ）**です。
> ビルド時に出力される製品JARからは自動的に除外（exclude）されるため、ゲーム実行時には影響しません。

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 プロジェクト概要
CrossTie は、RTM / NGTLib / MCTE (KaizPatchX)、Angelica、Bamboo、IntelliInput、GTNHLib、Hodgepodge、LiteLoader / Macro / Keybind Mod、WorldEdit、ProjectRed、CustomNPC+ など、複数 Mod にわたる**描画負荷削減・更新頻度抑制・互換性修正**を 1 つの JAR で提供します。

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
| **Gradle** | `9.6.1` |
| **必須Mod** | `UniMixins 0.3.1+` |
| **ビルドシステム** | RetroFuturaGradle 2.0.2 |
| **最終確認** | `2026-07-10` |

### 🔍 内部構造インデックス
* **Mixin 制御**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **Late Mixin 制御**: [`CrossTieLateMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieLateMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **検出可能Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`, `WorldEdit`, `ProjectRed`, `CustomNPC+`, `ATSAssist`, `SignPicture`, `ArchitectureCraft`

---

## 🚀 おすすめ構成

快適に動作させるための推奨Modバージョン構成です。

| Mod名 | 推奨バージョン | 区分 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **本体** |
| **UniMixins** | `0.3.1` | **必須** |
| **KaizPatchX** | `1.10.0` | 推奨 |
| **Angelica** | `2.1.51` | 推奨 |
| **GTNHLib** | `0.11.23+` | 推奨 |
| **Hodgepodge** | `2.7.171+` | 任意 |
| **ArchaicFix** | `0.8.0+` | 任意 |
| **ShaderFixer** | `5.4+` | 任意 |

---

## ⚡ 何をする Mod か

CrossTie は、RTM 関連の Mod 群とパフォーマンス系 Mod 群の間で発生する、以下の **4つのコア項目**をまとめて解決・提供します。

1. **🏃 FPS 最適化**
   * LargeRail の描画最適化、スクリプト実行最適化、ディスプレイリスト最適化など。
   * 詳細: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

2. **⏳ TPS / サーバー負荷最適化**
   * Train Entity の更新頻度最適化、ネットワーク負荷低減、オブジェクトプール最適化など。
   * 詳細: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

3. **🤝 互換性・描画バグ修正**
   * Angelica や OptiFine 環境下での RTM 描画バグ修正、その他周辺Modの競合修正。
   * 詳細:
     * [`doc/Angelica・GTNHLib関連互換性修正.md`](./doc/Angelica・GTNHLib関連互換性修正.md)
     * [`doc/OptiFine・FastCraft関連互換性修正.md`](./doc/OptiFine・FastCraft関連互換性修正.md)
     * [`doc/RTM・NGTLib関連_バグ修正.md`](./doc/RTM・NGTLib関連_バグ修正.md)
     * [`doc/その他周辺Mod互換性修正.md`](./doc/その他周辺Mod互換性修正.md)

4. **✨ 新機能の追加**
   * 再起動不要のモデルパック再読み込み機能、架線削除機能、車内放送用サウンドAPIの追加など。
   * 詳細: [`doc/新規機能の使い方/`](./doc/新規機能の使い方/)

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
クラスローダーの問題を避けるため、一部の Mixin は `CrossTieLateMixinPlugin` による遅延ロードで適用されます（例: ProjectRed）。

詳細なパッチ内容については、`doc/` ディレクトリ内の各ドキュメントをご参照ください。

---

## 📥 導入方法

1. ダウンロードした `CrossTie-*.jar` を `mods` フォルダに配置します。
2. 必須となる `UniMixins 0.3.1+` を `mods` フォルダに配置します。
3. 目的に応じて、RTM / Angelica / Bamboo / IntelliInput などの対象Modを追加します。
4. 通常通りゲームを起動します。

---

## 🛠️ ビルドと開発

このプロジェクトはビルドシステムとして **RetroFuturaGradle (RFG)** を使用しています。

### 🧱 ビルド手順
標準的なビルド手順です。コンパイルされた `.jar` ファイルは `build/libs/` 内に生成されます。
```bash
./gradlew build --no-daemon
```

### 💻 開発環境のセットアップ

#### IntelliJ IDEA
1. コマンドプロンプト等で以下のコマンドを実行し、IDEA用のプロジェクトファイルを生成します。
```bash
./gradlew idea
```
2. IntelliJ IDEA でフォルダを開き、プロジェクトをインポートします。

#### Eclipse
1. 以下のコマンドを実行し、Eclipse用のプロジェクトファイルを生成します。
```bash
./gradlew eclipse
```
2. Eclipse で「既存のプロジェクトをワークスペースへインポート」からプロジェクトを読み込みます。

### ▶️ 開発環境での実行
IDE 内で自動生成された実行設定（Run Configuration）を使用するか、以下のコマンドでテスト用クライアントを起動できます。
```bash
./gradlew runClient
```