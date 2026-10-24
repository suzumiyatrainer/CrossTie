# CrossTie Discord 連携機能 仕様書 & 取扱説明書

CrossTie に実装された Discord 連携機能の設計思想、アーキテクチャ、全クラス構成、設定項目、および導入・運用方法の総合ドキュメントです。

---

## 1. 概要と基本設計思想

CrossTie の Discord 連携機能は、Minecraft 1.7.10 の専用サーバー（Dedicated Server）と Discord サーバー間で、チャット・イベント通知・プレイヤー顔絵文字・画像プレビューなどを**完全非同期・安全かつ軽量**に双方向連携するシステムです。

### 主な特徴と設計思想
1. **完全オプトイン設計（デフォルト無効）**
   - `config/CrossTie/discord.cfg` で `enabled = true` に明示的に設定された場合のみ動作します。
   - 統合サーバー（シングルプレイ / LAN公開）では一切起動せず、クライアントやシングルプレイ環境の負荷やログを汚しません。
2. **外部ライブラリ依存ゼロ（Zero External Dependencies）**
   - JDA や Discord4J などの重量な外部ライブラリを一切追加せず、Minecraft 1.7.10 に組み込み済みの **Netty 4.0**、**Gson**、および **Java 8 標準 API** のみで構築されています。
   - 他の Mod や ライブラリ競合（Jackson や SLF4J などのバージョン衝突）を完全に排除しています。
3. **メインスレッド完全非ブロック（0ms 設計）**
   - すべての Discord API 通信（WebSocket 受信、Webhook 送信、絵文字同期、画像ダウンロード等）は独立したバックグラウンドスレッドで実行されます。
   - Minecraft サーバーのメインスレッド（TPS）への影響は実質ゼロです。
4. **トークンと秘密情報の安全管理**
   - トークンを生テキストで設定ファイルに直接書き込むだけでなく、外部ファイル（`file:...`）や環境変数（`env:...`）からの参照に対応しています。
   - ログ出力時は自動的にマスク（`Bot MT...****`）され、誤ってログを公開した場合のトークン流出を防止します。

---

## 2. 実装機能一覧

### ① 双方向チャット同期
- **Minecraft → Discord**:
  - プレイヤーの通常チャット、参加、退出、死亡メッセージを Discord に送信。
  - Webhook URL が設定されている場合は、発言したプレイヤーのスキン顔画像（アバター）とプレイヤー名で Webhook 発言。
  - Webhook 未設定時は Bot アカウントとしてテキスト送信。
- **Discord → Minecraft**:
  - Discord Gateway (WebSocket v10) を使用し、Discord 上の発言をミリ秒単位で即座に Minecraft サーバーへ同期。
  - メインスレッドの ServerTick（1 tick あたり最大 20 件スプレッド）で安全に `ChatComponent` を配信。

### ② 表示名（ニックネーム）多層フォールバック
Discord から Minecraft へのメッセージ受信時、発言者の表示名（`%displayname%`）は以下の優先順位で自動解決されます。
1. **サーバーニックネーム (`member.nick`)**: サーバー固有のニックネームが設定されていれば最優先。
2. **グローバル表示名 (`author.global_name`)**: ニックネーム未設定時はアカウント全体の表示名を使用。
3. **ユーザー名 (`author.username`)**: 表示名も未設定時は英数字のユーザー名を使用。
4. **フォールバック (`"Discord User"`)**: すべて取得できない場合の安全値。

### ③ Discord 画像の高画質ゲーム内プレビュー
Discord 上で送信された画像（PNG / JPG / JPEG / GIF / WebP / BMP）を Minecraft 内で即座に閲覧できる機能です。
- **チャットリンク**:
  - メッセージ末尾に `§b[🖼 ファイル名 (クリックでプレビュー)]` と `§7[ブラウザで開く]` が自動付与。
- **ゲーム画面中央ビューア ([GuiImagePreview](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/client/gui/GuiImagePreview.java))**:
  - アスペクト比を維持し、Minecraft 画面の中央に合わせて高画質（バイリニア補間）で全画面プレビュー。
  - **アニメーション GIF 対応**: GIF の全フレームと遅延時間を解析し、ゲーム内で滑らかにアニメーション再生。
  - **操作ガイド**:
    - `ESC` キー / 画面外クリック: 閉じる（VRAM テクスチャは即時解放）。
    - `Z` キー / クリック: 画面フィット表示 ⇔ 等倍（100%）表示のトグル切り替え（等倍時はマウスドラッグで移動可能）。
    - `B` キー: 外部ブラウザで元画像を開く。
- **Mod 未導入者への配慮**:
  - クライアントに CrossTie が入っていないプレイヤーがプレビューをクリックした場合、サーバー側フォールバックコマンドがブラウザで開くための直接リンクをチャットに案内します。

### ④ プレイヤースキン顔絵文字の自動同期 ([PlayerEmojiManager](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/discord/emoji/PlayerEmojiManager.java))
Minecraft プレイヤーの顔画像を Discord のカスタム絵文字（**Application Emoji 優先**、最大 2,000 スロット）として同期し、Discord チャット上で `<:MCID:emoji_id>`（プレフィックスなし・MCID 完全一致）として表示します。
- **命名規則**:
  - `mc_` などのプレフィックスは付けず、**MCID と完全一致** で作成されます。
- **既存絵文字の検出と自動更新（アプリ絵文字優先）**:
  - スキン変更時や初回作成時、Discord 上の Application Emoji および Guild Emoji の一覧を照合し、既存の同名絵文字（旧 `mc_` プレフィックス含む）が残っていれば **事前に削除してからアプリ絵文字を新規作成** します。これにより重複エラー（`APPLICATION_EMOJI_NAME_ALREADY_TAKEN`）を防ぎ、確実に最新スキンでアプリ絵文字に更新されます。
- **更新判定タイミング**:
  1. **毎サーバー起動時**: ローカルキャッシュ（`config/CrossTie/skin_cache.json`）内の全プレイヤーについて、サーバー停止中にスキンが変わっていないかを起動時に非同期で一括チェック。
  2. **プレイヤー参加試行時**: プレイヤーがサーバーに接続試行した瞬間（`ServerConfigurationManager.allowUserToConnect` を Mixin でフック）に先行して画像取得・同期を開始。ログイン完了メッセージ送信時には既に最新の絵文字タグが準備されます。
- **SHA-256 完全一致照合**:
  - 取得した顔画像バイト列の SHA-256 ハッシュを計算し、キャッシュと完全一致した場合は Discord API 通信（削除・作成）をスキップ。
- **60 秒クールダウン（デバウンス）**:
  - 参加試行直後のログイン完了や連続パケットによる重複リクエストを防止。

---

## 3. 設定ファイル仕様 (`config/CrossTie/discord.cfg`)

サーバー初回起動時、`config/CrossTie/discord.cfg` に以下のデフォルト設定が自動生成されます。

```properties
# Configuration file

general {
    # Discord 連携を有効化するかどうか (Dedicated Server のみ有効)。
    B:enabled=false

    # Discord Bot Token。「file:path/to/token」や「env:VAR_NAME」の形式も使用可能。
    S:botToken=file:config/CrossTie/discord.token

    # 連携対象の Discord サーバー (Guild) ID。
    S:guildId=

    # チャット送受信対象のテキストチャンネル ID。
    S:channelId=

    # リッチメッセージ送信用の Discord Webhook URL (空欄の場合は Bot API を使用)。
    S:webhookUrl=

    # プレイヤーの顔絵文字を自動作成・同期するかどうか。
    B:enablePlayerFaceEmojis=true

    # Application 絵文字を優先するかどうか (false の場合は Guild 絵文字を優先)。
    B:preferApplicationEmojis=true

    # プレイヤーのアバター/顔画像を取得する URL テンプレート。
    # プレースホルダー: {uuid}, {uuid-nodashes}, {name}, {size}
    S:avatarUrlTemplate=https://crafatar.com/avatars/{uuid-nodashes}?size=256&overlay
}

events {
    # プレイヤーのチャットを Discord に転送するかどうか。
    B:sendChat=true

    # プレイヤーの参加・退出メッセージを Discord に転送するかどうか。
    B:sendJoinLeave=true

    # プレイヤーの死亡メッセージを Discord に転送するかどうか。
    B:sendDeath=true

    # サーバーの起動・停止通知を Discord に転送するかどうか。
    B:sendServerLifecycle=true
}

formatting {
    # Discord から Minecraft へ受信時のメッセージフォーマット。
    S:formatDiscordToMc="§dDiscord§7｜%rolecolor%[%role%]§r %displayname%：%message%"

    # プレイヤーのチャットを Discord に送信する際のフォーマット。
    S:formatMcToDiscord="%emoji% **%mcid%**: %message%"

    # プレイヤー死亡時のフォーマット。
    S:formatPlayerDeath="%emoji% %deathmessage%"

    # プレイヤー参加時のフォーマット。
    S:formatPlayerJoin="## %emoji% **%mcid%** がサーバーに参加しました"

    # プレイヤー退出時のフォーマット。
    S:formatPlayerLeave="## %emoji% **%mcid%** がサーバーから退出しました"

    # サーバー起動時のフォーマット。
    S:formatServerStart="# :white_check_mark: **サーバーが起動しました**"

    # サーバー停止時のフォーマット。
    S:formatServerStop="# :octagonal_sign: **サーバーが停止しました**"
}
```

### 利用可能なフォーマット用プレースホルダー一覧

| プレースホルダー | 説明 | 利用可能な設定項目 |
| :--- | :--- | :--- |
| `%displayname%` | Discord 側の表示名（サーバーニックネーム ＞ グローバル表示名 ＞ ユーザー名） | `formatDiscordToMc` |
| `%role%` | Discord サーバーでの最上位ロール名（役職がない場合は自動省略） | `formatDiscordToMc` |
| `%rolecolor%` | Discord のロール色を最も近い Minecraft チャット色に自動変換したカラーコード | `formatDiscordToMc` |
| `%mcid%` | Minecraft プレイヤー名（旧 `%name%` と同等） | `formatMcToDiscord`, `formatPlayerJoin`, `formatPlayerLeave`, `formatPlayerDeath` |
| `%name%` | 互換性のためのプレイヤー名・Discord ユーザー名 | 全般 |
| `%message%` | チャット本文（画像のみの場合は自動で空白、画像外添付のみは `添付ファイル`） | `formatDiscordToMc`, `formatMcToDiscord` |
| `%emoji%` | プレイヤーの顔絵文字タグ（`<:mc_Player:id>`） | `formatMcToDiscord`, `formatPlayerJoin`, `formatPlayerLeave`, `formatPlayerDeath` |
| `%deathmessage%` | バニラの死亡ログ（例: `Player was slain by Zombie`） | `formatPlayerDeath` |

> [!NOTE]
> 設定値がダブルクオーテーション `""` で囲まれている場合でも、CrossTie の内部パーサーが前後のクォートを自動除去するため、Discord の Markdown 見出し書式（`# ` で文字が大きくなる機能）が正常に動作します。

---

## 4. コード構成とアーキテクチャ

関連するソースコードは `net.suzumiya.crosstie.discord` および周辺パッケージに集約されています。

```
src/main/java/net/suzumiya/crosstie/
├── CrossTie.java                                  # Mod メインクラス (コマンド登録・ライフサイクル管理)
├── command/
│   ├── CommandCrossTie.java                       # サーバー管理コマンド (/crosstie cfgr)
│   └── CommandViewImageFallback.java              # Mod 未導入プレイヤー向けフォールバックコマンド (/crosstieview)
├── client/gui/
│   ├── CommandViewImage.java                      # クライアント側画像プレビューコマンド (/crosstieview)
│   └── GuiImagePreview.java                       # 高画質・GIF対応 画面中央画像ビューア GUI
├── mixins/minecraft/
│   └── ServerConfigurationManagerLoginMixin.java  # 参加試行時 (allowUserToConnect) フック Mixin
└── discord/
    ├── DiscordConfig.java                         # 設定の読み込み・再読み込み・unquote・アバターURL解決
    ├── DiscordManager.java                        # 統合ライフサイクル管理 (起動・停止・各クライアント制御)
    ├── DiscordMessageQueue.java                   # スレッドセーフな双方向メッセージキュー
    ├── DiscordRoleManager.java                    # ギルドロール・ロール色管理 & MCカラー最近傍変換
    ├── DiscordSecretManager.java                  # トークン解決 (env:/file:)・ログマスク処理
    ├── client/
    │   ├── DiscordGatewayClient.java              # Netty 4.0 による Discord WebSocket Gateway (受信・画像抽出)
    │   └── DiscordWebhookClient.java              # REST / Webhook による非同期送信クライアント
    ├── emoji/
    │   └── PlayerEmojiManager.java                # 顔絵文字同期 (SHA-256照合、起動時・参加試行時チェック)
    └── listener/
        └── DiscordEventListener.java              # Forge / FML イベントリスナー (チャット、参加、退出、死亡、Tick)
```

### 主要コンポーネントの責務詳細

1. **[DiscordManager.java](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/discord/DiscordManager.java)**
   - `FMLServerStartingEvent` で専用サーバー判定および設定の有効状態を検証。
   - `PlayerEmojiManager.init`、`DiscordWebhookClient`（送信スレッド）、`DiscordGatewayClient`（受信WebSocket）、`DiscordEventListener` の初期化と起動を一元管理。
   - `FMLServerStoppingEvent` でサーバー停止通知を送信後、全スレッドおよびリソースを安全にクローズ。

2. **[DiscordGatewayClient.java](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/discord/client/DiscordGatewayClient.java)**
   - Discord Gateway v10 (`wss://gateway.discord.gg/?v=10&encoding=json`) に Netty 4.0 で接続。
   - Netty 4.0 の TLS 1.2 明示指定および Cloudflare / SNI パラメータを設定。
   - Heartbeat ループ、Opcode 7 (Reconnect)、Opcode 9 (Invalid Session)、Resume セッション復元を完全実装。
   - 受信した `MESSAGE_CREATE` ペイロードから、表示名のフォールバック解決および画像ファイル（`attachments`）の抽出を実施し、リッチテキスト（`ChatComponentText`）を構築してキューへ投入。

3. **[DiscordWebhookClient.java](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/discord/client/DiscordWebhookClient.java)**
   - `BlockingQueue<OutboundMessage>` からメッセージを取り出し、Webhook または Bot REST API (`/channels/{id}/messages`) で送信。
   - Discord のレートリミット（HTTP 429）を検出し、`Retry-After` に従った安全なバックオフ待機を実行。

4. **[PlayerEmojiManager.java](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/discord/emoji/PlayerEmojiManager.java)**
   - プレイヤー顔画像（Crafatar / mc-heads）のバイト列をダウンロードし、SHA-256 ハッシュを計算。
   - `config/CrossTie/skin_cache.json` のキャッシュ情報と比較し、変更があった場合のみ古い絵文字を削除して新しい絵文字を Discord API 経由で作成。
   - サーバー起動時に全キャッシュプレイヤーのスキン変更チェックを自動実行。
   - 参加試行フックと 60 秒クールダウンによる最適化。

5. **[ServerConfigurationManagerLoginMixin.java](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/mixins/minecraft/ServerConfigurationManagerLoginMixin.java)**
   - `ServerConfigurationManager#allowUserToConnect` の先頭（HEAD）にインジェクト。
   - 接続試行を行ったプレイヤーの `GameProfile`（UUID・名前）を取得し、非同期ワーカースレッドで先行してスキン同期を開始。

6. **[GuiImagePreview.java](file:///S:/【重要】すずみやの書類/mod開発/CrossTie/src/main/java/net/suzumiya/crosstie/client/gui/GuiImagePreview.java)**
   - クライアント側で `/crosstieview <url> [filename]` により起動される画像ビューア。
   - `ImageIO` と `ImageReader` による GIF アニメーション解析・フレーム進行制御。
   - OpenGL バイリニアフィルタによる高画質描画とアスペクト比保持センタリング。
   - メモリキャッシュ機構、ドラッグによるパン移動、等倍/フィット切り替え機能。

---

## 5. セットアップ・導入手順

### ステップ 1: Discord Bot の作成と設定
1. [Discord Developer Portal](https://discord.com/developers/applications) にアクセスし、「New Application」を作成します。
2. 左メニュー「Bot」を開き、「Reset Token」をクリックして **Bot Token** を取得します。
3. 同ページ「Privileged Gateway Intents」欄で以下を **有効化（ON）** します：
   - **SERVER MEMBERS INTENT**（サーバーメンバーのニックネーム取得に使用）
   - **MESSAGE CONTENT INTENT**（Discord メッセージ本文・画像の取得に使用）
4. 左メニュー「OAuth2」→「URL Generator」を開き、以下の権限にチェックを入れます：
   - `bot`
   - Permissions: `Send Messages`, `Embed Links`, `Attach Files`, `Read Message History`, `Manage Emojis and Stickers`（絵文字自動同期を使用する場合）
5. 生成された URL をブラウザで開き、対象の Discord サーバーに Bot を招待します。

### ステップ 2: チャンネル ID / サーバー ID の取得
1. Discord の設定 →「詳細設定」で「開発者モード」を有効にします。
2. 連携したい Discord サーバーのアイコンを右クリックし、「サーバー ID をコピー」で **Guild ID** を取得します。
3. チャット連携を行うテキストチャンネルを右クリックし、「チャンネル ID をコピー」で **Channel ID** を取得します。
4. （推奨）チャンネルの「設定」→「連携サービス」→「ウェブフック」から新規ウェブフックを作成し、「ウェブフック URL をコピー」で **Webhook URL** を取得します。

### ステップ 3: サーバー設定ファイルの編集
サーバーの `config/CrossTie/discord.cfg` を開き、以下のように設定します：

```properties
general {
    B:enabled=true
    S:botToken=ファイル指定または直接トークンを記述
    S:guildId=コピーしたサーバーID
    S:channelId=コピーしたチャンネルID
    S:webhookUrl=コピーしたウェブフックURL
    B:enablePlayerFaceEmojis=true
    B:preferApplicationEmojis=true
}
```

> [!TIP]
> トークンを Git や外部に誤公開しないため、`config/CrossTie/discord.token` というファイルを作成してその中にトークン文字列のみを書き込み、`S:botToken=file:config/CrossTie/discord.token` と指定することを強く推奨します。

### ステップ 4: サーバー起動と動作確認
1. Minecraft サーバーを起動します。
2. サーバーコンソールに以下のログが出力されれば連携成功です：
   ```
   [CrossTie] Starting Discord integration (Token: Bot MT...****)...
   [CrossTie] Application ID resolved: 123456789012345678
   [CrossTie] Connecting to Discord Gateway (gateway.discord.gg)...
   [CrossTie] Discord Gateway connected!
   [CrossTie] Discord Gateway authenticated successfully!
   [CrossTie] Integration successfully activated!
   ```
3. Discord 側に起動通知（`# :white_check_mark: **サーバーが起動しました**`）が送信されます。
4. Discord チャンネルに画像を投稿し、Minecraft 側のチャット欄に `[🖼 画像名 (クリックでプレビュー)]` が表示され、クリックして高画質プレビューが開くことを確認してください。

### 最後に
* バグや要望がありましたら、issueを立てていただけると助かります。