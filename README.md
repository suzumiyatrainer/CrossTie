# CrossTie

CrossTie は、Minecraft 1.7.10 向けの RTM 系最適化・互換パッチ Mod です。

RTM / KaizPatchX、Bamboo、Angelica、IntelliInput、NGTLib に対して、描画負荷の削減と一部互換修正をまとめて提供します。

## ステータス

| 項目 | 状態 |
| --- | --- |
| ビルド | [GitHub Actions](./.github/workflows/build-and-test.yml) でビルド定義あり。ローカルでは `./gradlew build --no-daemon` で確認済み |
| 対象 Minecraft | `1.7.10` |
| 対象 Forge | `10.13.4.1614` |
| 対象 Java | `8` |
| 必須基盤 | `UniMixins 0.2.1` |
| 対応 Mod | `RTM / KaizPatchX`, `Bamboo`, `Angelica`, `IntelliInput`, `NGTLib` |
| 設定実装 | [`CrossTieConfig.java`](./src/main/java/net/suzumiya/crosstie/config/CrossTieConfig.java) |
| Mixin 制御 | [`CrossTieMixinPlugin`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java) |
| 最終確認 | 2026-04-16 |

## 言語

- [日本語](#japanese)
- [English](#english)
- [한국어](#korean)
- [简体中文](#simplified-chinese)
- [繁體中文](#traditional-chinese)

## 何をする Mod か

CrossTie は、次の 2 系統をまとめて面倒を見ることを目的にしています。

- FPS 最適化
- TPS 最適化

RTM と Bamboo の更新・描画を、プレイヤーからの距離に応じて間引きます。
加えて、Angelica 環境でのディスプレイリスト処理や、RTM スクリプト内の `GL11` 呼び出しも補助します。

## 主な機能

### FPS 最適化

#### RTM

- 車両描画の距離カリング
- `TileEntity` 系の描画カリング
- レール系の描画距離調整
- `RenderEffect`、`RenderMirror`、`RenderPaint` などの追加レンダラも対象

#### Bamboo

- Entity の描画カリング
- `TileEntity` の描画カリング
- 更新頻度の抑制

#### Angelica 連携

- `hi03ExpressRailway` の描画中は、Angelica のディスプレイリスト処理を避けて OpenGL の旧経路を使う
- `PartsRenderer` のスクリプト実行前に GL コンテキストを整える
- RTM スクリプト中の `GL11` 呼び出しをブリッジ経由に書き換える

#### IntelliInput 連携

- `CallWindowProc` 系のネイティブコールバックを安定化
- IME 処理中の例外を抑制し、チャット入力時の不具合を避ける

### TPS 最適化

- RTM の列車・bogie 系更新を描画距離ベースで間引く
- Bamboo の `TileEntity` 更新を距離で抑制する
- MovingMachine は挙動に影響しやすいため、過度に削らない方針

## 対応環境

- Minecraft: 1.7.10
- Forge: 10.13.4.1614
- UniMixins: 0.2.1
- Java: 8

## 対応 Mod

### 必須

- UniMixins

### 対応済み

- RealTrainMod / KaizPatchX
- Bamboo
- Angelica
- IntelliInput
- NGTLib

### 依存解決

開発時は `libs/` に置かれた jar を使う構成です。
private Maven を使える場合は、`gradle.properties` の `privateMavenUrl` / `privateMavenUser` / `privateMavenPassword` で切り替えます。

## 導入方法

1. `CrossTie-*.jar` を `mods` フォルダに入れます。
2. `UniMixins` を入れます。
3. 必要に応じて RTM / Bamboo / Angelica / IntelliInput を入れます。
4. ゲームを起動します。

## 設定

初回起動後に `config/CrossTie.cfg` が生成されます。

### FPS

- `enableRenderCulling`
  - RTM / Bamboo の描画カリングを有効にします。
- `fixAngelicaRailCulling`
  - Angelica / Sodium 系でのレールカリング問題を避けるための補助設定です。

### TPS

- `enableTileEntityUpdates`
  - TileEntity の更新最適化を有効にします。

## 開発メモ

- Mixin ベースで実装しています。
- 対象 Mod の有無は起動時に判定し、入っているものだけを有効化します。
- `CrossTieMixinPlugin` でクライアント専用 Mixin と導入済み Mod を判別しています。

## ビルド

```bash
./gradlew build --no-daemon
```

<a id="japanese"></a>
## 日本語

CrossTie は、Minecraft 1.7.10 向けの RTM 系最適化・互換パッチ Mod です。
RTM / KaizPatchX、Bamboo、Angelica、IntelliInput、NGTLib に対して、描画負荷の削減と一部互換修正をまとめて提供します。

主な内容:

- RTM / Bamboo の距離カリング
- Angelica 用のディスプレイリスト互換
- RTM スクリプトの `GL11` ブリッジ
- IntelliInput のコールバック安定化

対応環境:

- Minecraft 1.7.10
- Forge 10.13.4.1614
- Java 8
- UniMixins 0.2.1

導入:

1. `CrossTie-*.jar` を `mods` に入れる
2. `UniMixins` を入れる
3. 必要なら RTM / Bamboo / Angelica / IntelliInput を入れる

設定:

- `enableRenderCulling`: 描画カリングを有効化
- `fixAngelicaRailCulling`: Angelica / Sodium 向けレールカリング補助
- `enableTileEntityUpdates`: TileEntity 更新最適化

<a id="english"></a>
## English

CrossTie is an optimization and compatibility patch mod for Minecraft 1.7.10 RTM-based packs.
It reduces render/update cost for RTM / KaizPatchX, Bamboo, Angelica, IntelliInput, and NGTLib.

Highlights:

- Distance culling for RTM and Bamboo renderers
- Angelica-compatible display list handling
- GL11 bridging for RTM scripts
- IntelliInput callback stabilization

Supported environment:

- Minecraft 1.7.10
- Forge 10.13.4.1614
- Java 8
- UniMixins 0.2.1

Install:

1. Put `CrossTie-*.jar` into `mods`
2. Install `UniMixins`
3. Add RTM / Bamboo / Angelica / IntelliInput if needed

Config:

- `enableRenderCulling`: enable render culling
- `fixAngelicaRailCulling`: Angelica / Sodium rail-culling helper
- `enableTileEntityUpdates`: enable TileEntity update optimization

<a id="korean"></a>
## 한국어

CrossTie는 Minecraft 1.7.10용 RTM 계열 최적화 및 호환 패치 Mod입니다.
RTM / KaizPatchX, Bamboo, Angelica, IntelliInput, NGTLib의 렌더링과 업데이트 부담을 줄입니다.

핵심 기능:

- RTM 및 Bamboo 렌더러 거리 컷
- Angelica 호환 디스플레이 리스트 처리
- RTM 스크립트의 GL11 브리지
- IntelliInput 콜백 안정화

지원 환경:

- Minecraft 1.7.10
- Forge 10.13.4.1614
- Java 8
- UniMixins 0.2.1

설치:

1. `CrossTie-*.jar`를 `mods` 폴더에 넣습니다.
2. `UniMixins`를 설치합니다.
3. 필요하면 RTM / Bamboo / Angelica / IntelliInput를 추가합니다.

설정:

- `enableRenderCulling`: 렌더 카링 활성화
- `fixAngelicaRailCulling`: Angelica / Sodium용 레일 카링 보조
- `enableTileEntityUpdates`: TileEntity 업데이트 최적화

<a id="simplified-chinese"></a>
## 简体中文

CrossTie 是一个面向 Minecraft 1.7.10 的 RTM 系优化与兼容补丁 Mod。
它为 RTM / KaizPatchX、Bamboo、Angelica、IntelliInput、NGTLib 提供渲染与更新优化。

主要功能：

- RTM 与 Bamboo 的距离裁剪
- 兼容 Angelica 的显示列表处理
- RTM 脚本中的 GL11 桥接
- IntelliInput 回调稳定化

支持环境：

- Minecraft 1.7.10
- Forge 10.13.4.1614
- Java 8
- UniMixins 0.2.1

安装：

1. 将 `CrossTie-*.jar` 放入 `mods`
2. 安装 `UniMixins`
3. 如有需要，再加入 RTM / Bamboo / Angelica / IntelliInput

配置：

- `enableRenderCulling`：启用渲染裁剪
- `fixAngelicaRailCulling`：Angelica / Sodium 轨道裁剪辅助
- `enableTileEntityUpdates`：启用 TileEntity 更新优化

<a id="traditional-chinese"></a>
## 繁體中文

CrossTie 是一個面向 Minecraft 1.7.10 的 RTM 系最佳化與相容補丁 Mod。
它為 RTM / KaizPatchX、Bamboo、Angelica、IntelliInput、NGTLib 提供渲染與更新最佳化。

主要功能：

- RTM 與 Bamboo 的距離裁剪
- 相容 Angelica 的顯示列表處理
- RTM 腳本中的 GL11 橋接
- IntelliInput 回呼穩定化

支援環境：

- Minecraft 1.7.10
- Forge 10.13.4.1614
- Java 8
- UniMixins 0.2.1

安裝：

1. 將 `CrossTie-*.jar` 放入 `mods`
2. 安裝 `UniMixins`
3. 如有需要，再加入 RTM / Bamboo / Angelica / IntelliInput

設定：

- `enableRenderCulling`：啟用渲染裁剪
- `fixAngelicaRailCulling`：Angelica / Sodium 軌道裁剪輔助
- `enableTileEntityUpdates`：啟用 TileEntity 更新最佳化

## Thanks

- hi03
- Kaiz_JP
- GTNewHorizons team
