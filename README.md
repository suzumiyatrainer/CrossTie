# CrossTie - RTM & Bamboo Optimization Patch

Minecraft 1.7.10環境におけるRealTrainMod (RTM) および BambooMod の動作を軽量化・安定化するための最適化パッチModです。
大規模な鉄道ネットワークや和風建築ワールドにおけるFPS低下（描画負荷）とTPS低下（サーバー負荷）の双方を改善します。

## 主な機能

### 1. FPS最適化 (描画軽量化)
プレイヤーの描画距離設定に基づき、遠くのオブジェクトを描画しない（カリング）処理を追加しました。

#### RealTrainMod (RTM)
(RTM導入時のみ有効)
- **車両描画カリング**: 描画距離外にある車両（台車含む）のレンダリングを完全にスキップします。長大編成でも手元の車両のみ描画されるため軽快です。
- **TileEntity描画カリング**: 以下の重いオブジェクトを描画距離外で非表示にします。
  - 券売機、改札機 (`RenderMachine`)
  - 架線、コネクタ (`RenderElectricalWiring`)
  - 駅名標、看板 (`RenderStation`, `RenderSignBoard`)
  - 遮断機、ターンテーブル (`RenderMovingMachine`)
  - **追加最適化**: 光源エフェクト(`RenderEffect`)、鏡(`RenderMirror`)、塗装ブロック(`RenderPaint`)、パイプ、レール部品(`PartsRenderer`)など、市街地で負荷となりやすい装飾類も網羅的にカリングします。
- **信号機/レール**: 描画距離制限をバニラ設定に合わせることで、無駄な遠距離描画をカットしました。

#### BambooMod(竹Mod)
(BambooMod導入時のみ有効)
- **TileEntity描画カリング**: 囲炉裏、行灯、石臼、布団などの描画を最適化。
- **Entity描画カリング**: 蛍(`RenderFirefly`)、風車、水車、桜の花弁などの動的エンティティもカリング対象に追加。

#### Angelica
(Angelica導入時のみ有効)
- **レール描画不具合の修正**: Angelica (1.0.0-beta72以降) と hi03様作成、**hi03ExpressRailwayPack** 系列を併用した際に発生する描画崩れを修正しました。

### 2. TPS最適化 (サーバー/処理負荷軽減)
内部処理の無駄を省き、サーバーおよびクライアントの計算負荷を軽減します。

- **RTM列車処理の間引き**: 静止中の列車や台車の物理演算・位置更新処理をスキップまたは簡略化。
- **編成更新の最適化**: 連結情報の同期頻度を調整。
- **サーバー側Bamboo最適化**: サーバー側において、竹ModのTileEntity（囲炉裏など）の更新処理を **1/2の頻度に間引き** ました。これによりチャンクローダー等で読み込まれた無人エリアの負荷が半減します（調理時間は伸びますが停止はしません）。

### 3. サーバー互換性
- **クラッシュ回避**: クライアント専用の描画コードを適切に分離し、サーバー(`minecraft_server.jar`)のmodsフォルダに入れてもクラッシュしない設計にアップデートしました。
- **導入推奨**: 基本的にはクライアント導入で効果を発揮しますが、サーバーへの導入も安全です（Bamboo最適化が有効になります）。

## 導入方法
1. `CrossTie-x.x.x.jar` を `mods` フォルダに入れてください。
2. 前提Mod: **UniMixins** が必須です。

## 互換性情報
- **Forge**: 10.13.4.1614
- **UniMixins**: 0.2.1
- **OptiFine**: 未テスト
- **FastCraft**: 未テスト
- **FalseTweaks**: 未テスト
- **Beddium**: 未テスト
- **SwanSong**: 未テスト
- **Angelica**: 2.0.0-alpha16
- **RealTrainMod(純正)**: 未テスト
- **RealTrainMod(KaizPatchX)**: 1.9.3
- **BambooMod**: 2.6.8.5

## 開発者向け情報
- Mixinベースで動作し、元のModファイルを書き換えずにバイトコードを注入しています。
- **Late Mixin**: 他のModとの互換性を高めるため、一部のパッチ（Bamboo関連）は Late Mixin として実装されています。
- `CrossTieMixinPlugin`: 導入されているMod（Angelica, Bamboo）を検出し、必要なパッチのみを適用する安全機構を備えています。

## Thanks
- **hi03様**: hi03ExpressRailwayPack系列のJavaScriptコードを参考にしました。
- **Kaiz_JP様**: KaizPatchXのコードを分析・参考にしました。
- **GTNewHorizonチーム**: Angelicaのコードを分析・参考にしました。