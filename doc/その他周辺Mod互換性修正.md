# その他周辺Mod（ATSAssistMod, CustomNPC+, 竹Mod, JourneyMap, RecipePlusMod, LWJGL3ify等）互換性修正仕様書

## 1. 概要
Minecraft 1.7.10 の鉄道系・建築系環境で併用されることが多い周辺Mod（**ATSAssistMod**、**CustomNPC+**、**竹Mod (Bamboo)**、**SignPicture**、**ArchitectureCraft**、**JourneyMap**、**Hodgepodge**、**RecipePlusMod**、**LWJGL3ify / IntelliInput** 等）と、RTM や CrossTie 環境を同時に運用した際に発生するクラッシュ・フリーズ・表示崩れ・入力不具合を修正するパッチ群です。

> [!NOTE]
> **MCTE (MC Terrain Editor) および RTM/NGTLib 関連の修正・最適化について**:
> MCTE, RTM, NGTLib 関連の機能はすべて [KaizPatchX関連_バグ修正](/doc/KaizPatchX関連_バグ修正.md) および [KaizPatchX関連_パフォーマンス最適化](/doc/KaizPatchX関連_パフォーマンス最適化.md) へ統合・集約されました。

---

## 2. ATSAssistMod 関連の修正および最適化

ATSAssistMod（RTM向けのIFTTT信号・ATS制御補助Mod）におけるスクリプト恒久ロックの解除、ブレーキノッチ制御、およびシリアライザの最適化を行います。

### 2.1 JavaScriptエラーによるデッドロック（恒久停止）の修正
- **対象ファイル**: `ATSAssistJavaScriptUnlockMixin.java`
- **問題の背景**:
  ATSAssistModのIFTTTコンテナが抱えるJavaScriptスクリプトに一度でも実行エラーが発生すると、内部の `error` フラグが `true` に設定されてNBTに永続化されます。このフラグが立つと、GUI編集（`setJSText()`）以外にリセットする手段がなく、スクリプトを修正しても再起動しても `doThat()` が実行されない状態（恒久デッドロック）に陥っていました。
  また、`scriptEngine`（スクリプトエンジン）は一度コンパイルされると使い回され続けますが、`setJSText()` を呼んでも `null` に戻されないため、壊れたスクリプト内容のままのエンジンが焼き付く問題もありました。
- **修正内容**:
  `doThat()` の先頭に割り込み、一定間隔（20tick = 約1秒ごと）で `error` フラグと `scriptEngine` を自動リセットするように修正しました。これにより、スクリプトを修正した場合や一時的なエラーが原因の場合に、次の再試行サイクルで正常に再コンパイル・実行されます。
- **結果**:
  ATSAssistModのJavaScriptスクリプトが一度エラーになっても自動回復するようになり、スクリプト修正後の反映が即座に行われます。

### 2.2 ATSAssist TASC 自動定位停止ブレーキ制御の統合
- **対象ファイル**: `EntityTrainBaseMixin.java`
- **概要**:
  ATSAssistModの `TrainController` が `TASCController`（定位停止制御：TASC）を有効化している場合、列車の速度更新処理（`EntityTrainBase.updateSpeed()`）にフックして、現在速度と停止目標距離から必要減速度を計算し、適切なブレーキノッチを自動選択して設定します。
- **技術的内容**:
  - リフレクション経由（`ATSAssistReflectionHelper`）で `TrainController` → `TASCController` を取得し、制動中かどうか・停止目標距離を読み取ります。
  - 列車設定（`TrainConfig.deccelerations`）から各ブレーキノッチの減速度を算出し、目標減速度以上の最小ノッチを選択します（最大常用制動段）。
  - 停止位置判定時はホールドブレーキ（最小段）に移行します。
- **結果**:
  ATSAssistModのTASC機能が有効な場合に、列車が停止目標位置に対して物理的に正確なブレーキング制御を行えるようになります。

### 2.3 IFTTTUtil における Jackson ObjectMapper シリアライザの最適化
- **対象ファイル**: `IFTTTUtilMixin.java`
- **問題の背景**:
  ATSAssistMod の `IFTTTUtil` は、`IFTTTContainer` のバイナリ/JSON シリアライズ・デシリアライズ（`convertClassSafe`）のたびに `new ObjectMapper()` を新規生成していました。Jackson の `ObjectMapper` は構築時に非常に重いリフレクション解析とモジュールスキャンを行うため、シリアライズが頻繁に発生すると大きなCPU負荷とメモリ確保が発生していました。
- **修正内容**:
  `convertClassSafe` メソッド内の `new ObjectMapper()` 呼び出しを `@Redirect` でキャッチし、静的な単一インスタンス（`crosstie$objectMapper`）へリダイレクトしました。
- **結果**:
  IFTTTコンテナのシリアライズ・デシリアライズ時のリフレクションおよびインスタンス生成コストが完全に排除され、CPU負荷とメモリ消費が削減されます。

---

## 3. CustomNPC+ 関連のバグ修正

CustomNPC+（カスタムNPC追加Mod）において、Java 8 環境での起動時クラッシュを引き起こすバグがありました。

### 3.1 ScriptEngineManagerの初期化によるUnsupportedClassVersionErrorクラッシュの修正
- **対象ファイル**: `ScriptControllerInitMixin.java`
- **問題の背景**:
  CustomNPC+ の `ScriptController` コンストラクタは、スクリプト機能の有効・無効チェック（`isScriptingEnabled()` の確認）よりも前に `new ScriptEngineManager()` を呼び出します。このコンストラクタはJava SPI（ServiceLoader）を通じて利用可能な全スクリプトエンジンファクトリを即座にスキャンするため、CustomNPC+ JARに同梱されている Java 11+ でコンパイルされた `org.openjdk.nashorn.NashornScriptEngineFactory` が検出されてしまい、Java 8 環境では `UnsupportedClassVersionError` が発生してサーバーがクラッシュしていました。
- **修正内容**:
  `ScriptController` コンストラクタ内の `new ScriptEngineManager()` を Mixin でインターセプトし、CustomNPC+ の設定（`ConfigScript.ScriptingEnabled`）が `false`（デフォルト）の場合は `new ScriptEngineManager(null)` を返すように差し替えました。`null` ClassLoader を渡すと ServiceLoader はブートストラップ CL のみを使用するため、アプリケーション CL 上のサードパーティSPI実装（Java 11+ 版 Nashorn）がロードされません。スクリプトを明示的に有効化（`true`）している場合のみ通常の完全初期化を実行します。
- **結果**:
  Java 8 環境でも CustomNPC+ を導入してサーバー・クライアントを起動できるようになります。スクリプト機能を使用しないユーザーが余分なJava 11+ 専用クラスのロードで引き起こされるクラッシュを完全に防止します。

---

## 4. 竹Mod (Bamboo) 関連のパッチおよびシェーダー互換

### 4.1 温泉水（BlockSpaWater）への OptiFine / Angelica シェーダー水面効果の適用
- **対象ファイル**: `MixinBlockSpaWater.java`
- **問題の背景**:
  竹Modの温泉水ブロック（`BlockSpaWater`）は独自のマテリアル定義を持っていたため、OptiFine や Angelica（Iris）などのシェーダー環境下において「水」として判定されず、シェーダーパックが提供する美しい波・光の反射・屈折効果が適用されない問題がありました。
- **修正内容**:
  `BlockSpaWater.getMaterial()` を `@Overwrite` で置換し、強制的にバニラの水マテリアル（`Material.water`）を返すように修正しました。
- **結果**:
  竹Modの温泉水に対して、OptiFine や Angelica のシェーダーパックによる波や水面反射、透明感のあるグラフィック効果が正しく適用されるようになります。

### 4.2 キャンプファイヤー描画の最適化（スタブ）
- **対象ファイル**: `BambooRenderCampfireMixin.java`
- **概要**:
  竹Mod（`ruby.bamboo`）のキャンプファイヤーや和風光源ブロックの TileEntity レンダラー（`RenderCampfire`）において、プレイヤーの視界外や遠距離での描画を間引くフックが存在します。

---

## 5. SignPicture 関連のパッチ

### 5.1 看板画像レンダラーの最適化（スタブ）
- **対象ファイル**: `SignPictureRendererMixin.java`
- **概要**:
  SignPicture Mod（`com.kamesuta.mc.signpic`）のカスタム看板画像レンダラー（`CustomTileEntitySignRenderer`）において、未ロード画像や視界外画像の描画を効率化するフックが存在します。

---

## 6. ArchitectureCraft 関連のパッチ

### 6.1 ベースモデルレンダラーの最適化（スタブ）
- **対象ファイル**: `BaseModelRendererMixin.java`
- **概要**:
  ArchitectureCraft（`gcewing.architecture`）のベースモデルレンダラー（`BaseModelRenderer`）において、ブロック描画時の不可視面計算のキャッシュおよび`Vector3`/`Trans3`オブジェクトのアロケーション削減を行うフックが存在します。

---

## 7. JourneyMap 関連の互換性修正と最適化

### 7.1 列車名表示（ターゲットマーカー）の直接描画と競合修正
- **対象ファイル**: `WaypointBeaconRendererMixin.java`, `DataCacheMixin.java`
- **問題の背景**:
  一部の環境や古い実装において、JourneyMap 上に列車名（TargetTrainId）を表示させる際、別スレッドから共有リスト（Stringリスト等）を操作してレンダラー側で読み取る方式がとられていました。これにより `ConcurrentModificationException` によるクラッシュや、データの同期ズレ、無駄なリスト確保によるメモリ消費が発生していました。
- **修正内容**:
  JourneyMap の 3Dウェイポイント/ビーコンレンダラー（`WaypointBeaconRenderer`）の描画処理に直接介入し、共有リストを使用せずに `EntityTrainBase` の持つ `trainName` などを OpenGL を用いて直接 JourneyMap のコンテキスト上に描画する方式へと根本から変更しました。
- **結果**:
  マルチスレッド環境下でもクラッシュや同期ズレが発生せず、安全かつ軽量に列車のマーカーや名前が JourneyMap の画面上に表示されるようになります。

### 7.2 GTNHLib（最新 DFU）併用時のウェイポイント保存・読み込みクラッシュ修正
- **対象ファイル**: `CrossTieClassTransformer.java`, `JourneyMapDfuBridge.java`, `Jm12NbtOpsHelper.java`
- **問題の背景**:
  GTNHLib が導入する最新版の DataFixerUpper（DFU）では `DataResult` がクラスからインターフェースへ変更されています。旧版を前提に作られた JourneyMap 1.7.10 と併用すると、ウェイポイントの保存や読み込み時に `IncompatibleClassChangeError` が発生してクラッシュしていました。
- **修正内容**:
  ASM トランスフォーマーで `journeymap.*` 配下の該当呼び出しを安全なブリッジクラス（`JourneyMapDfuBridge`）経由へリダイレクトし、新旧 DFU の差異を吸収しました。
- **結果**:
  最新の GTNHLib 環境でも JourneyMap のウェイポイント機能をクラッシュせずに利用できます。

---

## 8. Hodgepodge 関連のバグ修正

### 8.1 チャットGUI表示時のクラッシュ（NullGuard）修正
- **対象ファイル**: `ChatHandlerNullGuardMixin.java`, `ChatComponentStyleNullGuardMixin.java`, `ChatComponentTextNullGuardMixin.java`
- **問題の背景**:
  最適化Mod「Hodgepodge」を導入した環境において、MinecraftのチャットGUIを開いた際や特定のチャットメッセージ（チャットスタイル）を受信・処理した際に、`NullPointerException` が発生してゲームがクラッシュする問題がありました。
- **修正内容**:
  チャットのスタイルやテキスト処理のコア部分に対してNullガード（値がnullだった場合の安全な早期リターン処理やフォールバック）を注入し、Hodgepodgeの内部最適化処理とバニラの描画処理の間の不整合を吸収しました。
- **結果**:
  Hodgepodge導入環境下でも、チャットGUIを安全に開くことができ、クラッシュすることなくプレイを継続できます。

---

## 9. RecipePlusMod 関連の透過ガラス・板ガラス互換

### 9.1 ガラス・氷系ハーフ/1/4/階段ブロックの隣接面カリングと板ガラス接続対応
- **対象ファイル**: `RecipePlusGlassCompat.java`, `IRecipePlusGlassBlock.java`, `BlockBreakableRecipePlusMixin.java`, `BlockPaneRecipePlusMixin.java`, `BlockExSlabGlassMixin.java`, `BlockExStairsGlassMixin.java`
- **問題の背景**:
  RecipePlusMod が追加するガラス・色付きガラス・氷系のハーフブロック・1/4ブロック・階段は、バニラのガラスと隣り合わせに置いたときに接合面が消えず二重に見えたり、板ガラスが正しく接続されなかったりする問題がありました。
- **修正内容**:
  - 透過ガラス・氷系ブロック同士の接触面を判定し、バニラガラスと同様に不要な接合面を非表示（カリング）にする互換ヘルパーを追加しました。
  - バニラの板ガラス（`BlockPane`）が RecipePlusMod のハーフ・1/4・階段ブロックの面にも自然に接続できるようにしました。
- **結果**:
  RecipePlusMod のガラス系建材をバニラガラスや板ガラスと組み合わせても、継ぎ目がすっきり綺麗に表示されます。

---

## 10. LWJGL3ify / SDL3 IME 入力互換および IntelliInput 起動時フリーズ修正

### 10.1 LWJGL3ify（SDL3）環境での日本語 IME 入力・インライン変換オーバーレイ
- **対象ファイル**: `ImeCompat.java`, `SdlImeBridge.java`, `ImeTicker.java`, `GuiScreenImeMixin.java`, `GuiTextFieldImeMixin.java`, `GuiTextFieldCustomImeMixin.java`, `Lwjgl3ifyImeKeyboardMixin.java`
- **問題の背景**:
  Java 17/21/25 環境で Minecraft 1.7.10 を動かす LWJGL3ify（SDL3）環境では、テキスト入力欄（チャット・看板・RTM 各種 GUI 等）で日本語 IME の変換候補窓が画面の端へ飛んだり、変換中の未確定文字列が見えなくなったりする問題がありました。
- **修正内容**:
  - `SdlImeBridge` を通じて SDL3 のネイティブ IME 機能と連携し、入力欄のフォーカス状態に応じて IME を自動オン/オフし、候補窓の位置をカーソル付近へ通知します。
  - 変換中の文字列（未確定テキスト）をテキスト欄の上にリアルタイム描画するインライン変換オーバーレイを実装しました。
- **結果**:
  LWJGL3ify 環境でも、チャットや RTM の各種テキスト入力欄で快適に日本語入力・変換が行えるようになります。

### 10.2 IntelliInput の起動時フリーズ回避
- **対象ファイル**: `IntelliInputCompat.java`, `CrossTieClassTransformer.java`
- **問題の背景**:
  IntelliInput は起動時に OS 上の全ウィンドウに対して `GetWindowText` を問い合わせるため、応答しない他プロセスのウィンドウ（IDE や親プロセス等）があると永久に待ち続けて Minecraft が起動途中でフリーズすることがありました。
- **修正内容**:
  ASM パッチで `IntelliInput$1` の `EnumWindows` コールバックを安全なラッパーへ差し替え、自プロセス以外のウィンドウには問い合わせを行わないようにしました。
- **結果**:
  他アプリを起動したままでも、IntelliInput 導入環境で Minecraft が起動時にフリーズしなくなります。

