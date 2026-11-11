# その他周辺Mod（ATSAssistMod, CustomNPC+, 竹Mod, SignPicture, ArchitectureCraft等）互換性修正仕様書

## 1. 概要
Minecraft 1.7.10 の鉄道系・建築系環境で併用されることが多い、周辺支援Mod（**ATSAssistMod**、**CustomNPC+**、**竹Mod (Bamboo)**、**SignPicture**、**ArchitectureCraft** 等）と、RTMやCrossTie環境を同時に運用した際に発生する、処理遅延（ラグ）やデータ破損、機能制限などのバグを修正するパッチ群です。

> [!NOTE]
> **MCTE (MC Terrain Editor) および RTM/NGTLib 関連の修正・最適化について**:
> MCTE, RTM, NGTLib 関連の機能はすべて [KaizPatchX関連_バグ修正](/doc/KaizPatchX関連_バグ修正.md) および [KaizPatchX関連_パフォーマンス最適化](/doc/KaizPatchX関連_パフォーマンス最適化.md) へ統合・集約されました。

---

## 2. ATSAssistMod 関連の修正および最適化

ATSAssistMod（RTM向けのIFTTT信号・ATS制御補助Mod）におけるスクリプト恒久ロックの解除、ブレーキノッチ制御、およびシリアライザの最適化を行います。

### 3.1 JavaScriptエラーによるデッドロック（恒久停止）の修正
- **対象ファイル**: `ATSAssistJavaScriptUnlockMixin.java`
- **問題の背景**:
  ATSAssistModのIFTTTコンテナが抱えるJavaScriptスクリプトに一度でも実行エラーが発生すると、内部の `error` フラグが `true` に設定されてNBTに永続化されます。このフラグが立つと、GUI編集（`setJSText()`）以外にリセットする手段がなく、スクリプトを修正しても再起動しても `doThat()` が実行されない状態（恒久デッドロック）に陥っていました。
  また、`scriptEngine`（スクリプトエンジン）は一度コンパイルされると使い回され続けますが、`setJSText()` を呼んでも `null` に戻されないため、壊れたスクリプト内容のままのエンジンが焼き付く問題もありました。
- **修正内容**:
  `doThat()` の先頭に割り込み、一定間隔（20tick = 約1秒ごと）で `error` フラグと `scriptEngine` を自動リセットするように修正しました。これにより、スクリプトを修正した場合や一時的なエラーが原因の場合に、次の再試行サイクルで正常に再コンパイル・実行されます。
- **結果**:
  ATSAssistModのJavaScriptスクリプトが一度エラーになっても自動回復するようになり、スクリプト修正後の反映が即座に行われます。

### 3.2 ATSAssist TASC 自動定位停止ブレーキ制御の統合
- **対象ファイル**: `EntityTrainBaseMixin.java`
- **概要**:
  ATSAssistModの `TrainController` が `TASCController`（定位停止制御：TASC）を有効化している場合、列車の速度更新処理（`EntityTrainBase.updateSpeed()`）にフックして、現在速度と停止目標距離から必要減速度を計算し、適切なブレーキノッチを自動選択して設定します。
- **技術的内容**:
  - リフレクション経由（`ATSAssistReflectionHelper`）で `TrainController` → `TASCController` を取得し、制動中かどうか・停止目標距離を読み取ります。
  - 列車設定（`TrainConfig.deccelerations`）から各ブレーキノッチの減速度を算出し、目標減速度以上の最小ノッチを選択します（最大常用制動段）。
  - 停止位置判定時はホールドブレーキ（最小段）に移行します。
- **結果**:
  ATSAssistModのTASC機能が有効な場合に、列車が停止目標位置に対して物理的に正確なブレーキング制御を行えるようになります。

### 3.3 IFTTTUtil における Jackson ObjectMapper シリアライザの最適化
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

