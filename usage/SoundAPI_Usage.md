# CrossTie Sound API 使い方・連携仕様書

このドキュメントでは、CrossTieの非破壊・超軽量な音響API（`SoundManager`）の実際の使い方と、ATSAssistMod等のJavaScript（Nashorn）から連携する方法を解説します。

## 1. JavaScript側（ATSAssistMod）からの呼び出し方

JSスクリプトで `SoundAPI` を使用する前に、明示的に `SoundManager` のインスタンスを取得してください。

取得方法は以下のいずれかを使用できます。

**方法A：パッケージをインポートして取得（推奨）**
```javascript
importPackage(Packages.net.suzumiya.crosstie.api.sound);
var SoundAPI = SoundManager.getInstance();
```

**方法B：フルパスで直接取得**
```javascript
var SoundAPI = Packages.net.suzumiya.crosstie.api.sound.SoundManager.getInstance();
```

### 1.1 車内放送・車外スピーカーAPI
車両のドア状態や立ち位置に応じて音量がリアルタイムに変化する、非常に高度な追従型サウンドを再生します。

最大可聴範囲（`maxRadius`）を指定可能な **引数5つのシグネチャ** と、省略してデフォルト値を適用できる **引数4つのシグネチャ** のオーバーロードに対応しています。

```javascript
/**
 * @param {EntityTrainBase} train - 対象の列車インスタンス
 * @param {float} length - 車両の長さ（16.0, 18.0, 20.0 等）
 * @param {float} [maxRadius] - 最大可聴範囲（ブロック数）。省略時はデフォルト値を適用（車内漏洩: 15.0 / 車外放送: 30.0）
 * @param {String[]} sounds - 再生するサウンド名の配列
 * @param {int[]} delaySeconds - 各サウンドの再生遅延（**秒単位**。例: 0なら即時、3なら3秒後）
 */
```

#### パターンA：省略時（引数4つ。デフォルトの可聴範囲を使用）
```javascript
importPackage(Packages.net.suzumiya.crosstie.api.sound);
var SoundAPI = SoundManager.getInstance();

function doThat(tile, train, first) {
    var sounds = ["sound_test:xxx.soundfileN", "sound_test:xxx.soundfileN2"];
    var delays = [0, 3];

    // 車内放送の再生（デフォルトの可聴範囲 15.0m が適用されます）
    SoundAPI.playInCarAnnouncement(train, 20.0, sounds, delays);

    // 車外スピーカーの再生（デフォルトの可聴範囲 30.0m が適用されます）
    var extSounds = ["sound_test:xxx.soundfileN3"];
    var extDelays = [0];
    SoundAPI.playExteriorSound(train, 20.0, extSounds, extDelays);
}
```

#### パターンB：個別指定時（引数5つ。可聴範囲をメートル単位で微調整）
```javascript
importPackage(Packages.net.suzumiya.crosstie.api.sound);
var SoundAPI = SoundManager.getInstance();

function doThat(tile, train, first) {
    var sounds = ["sound_test:xxx.soundfileN", "sound_test:xxx.soundfileN2"];
    var delays = [0, 3];

    // 車内放送の再生（最大可聴範囲を 25.0m に広げて流す例）
    SoundAPI.playInCarAnnouncement(train, 20.0, 25.0, sounds, delays);

    // 車外スピーカーの再生（最大可聴範囲を 40.0m に広げて流す例）
    var extSounds = ["sound_test:xxx.soundfileN3"];
    var extDelays = [0];
    SoundAPI.playExteriorSound(train, 20.0, 40.0, extSounds, extDelays);
}
```

### 1.2 駅放送・地上子API
任意の複数の座標（ホームの端から端まで設置されたスピーカー等）から、一斉に音声を流します。最大聞こえ半径を指定可能です。

```javascript
/**
 * @param {int} dimension - ディメンションID（通常は train.dimension）
 * @param {double[][]} coords - 再生する座標のリスト [[X, Y, Z], [X, Y, Z], ...]
 * @param {float} maxRadius - 音が聞こえる最大の距離（ブロック数）
 * @param {String[]} sounds - サウンド配列
 * @param {int[]} delaySeconds - 遅延（秒単位）配列
 */

function doThat(tile, train, first) {
    // 2次元配列も純粋なJSの書き方そのままでOKです。内部で自動解釈されます。
    var coords = [
        [100.5, 65.0, 200.5],
        [120.5, 65.0, 200.5],
        [140.5, 65.0, 200.5]
    ];
    var maxRadius = 30.0;
    
    var sounds = ["sound_test:xxx.soundfileN4"];
    var delays = [0];

    // 1. ループなし単発再生
    SoundAPI.playStationBroadcast(train.dimension, coords, maxRadius, sounds, delays);

    // 2. ループ再生
    // 第1引数にループID（任意の文字列）を指定。最後の引数は間隔（秒単位。0なら隙間なしの無限ループ）
    SoundAPI.playStationLoop("sound_test:xxx.soundfileN5", train.dimension, coords, maxRadius, "sound_test:xxx.soundfileN5", 0);
}
```

### 1.3 ループの停止
```javascript
// 指定したループIDの音声を強制停止する
SoundAPI.stopStationLoop("sound_test:xxx.soundfileN5");
```

### 1.4 変数を用いた動的なサウンド指定（種別・行先など）
実際の鉄道運用では、列車の「種別」や「行先」を変数として取得し、それに一致する音声パーツを連結して再生することがよくあります。
JavaScriptの文字列結合（`+`）を使うことで、配列内に変数を直接組み込んで動的にサウンドを呼び出すことができます。

```javascript
function doThat(tile, train, first) {
    // 例：列車の状態から種別や行先番号を取得したと仮定します
    var type = 11; // 快速
    var dist = 6;  // つくば
    
    // 変数を使ってサウンドアドレスを動的に生成し、配列に格納します
    var sounds = [
        "sound_tsukubaexp:ann.t" + type, // 自動で "sound_tsukubaexp:ann.t11" になります
        "sound_tsukubaexp:ann.d" + dist  // 自動で "sound_tsukubaexp:ann.d6" になります
    ];
    var delays = [0, 4]; // 種別放送の4秒後に行先放送を流すなどの遅延指定

    // あとはそのままAPIに渡すだけでOKです！
    SoundAPI.playInCarAnnouncement(train, 20.0, sounds, delays);
}
```

### 1.5 編成・単行に対応した実践的な一斉車内放送の記述例
RTMで複数車両を連結して運転する場合、`train.getFormation()` メソッドで編成内のすべての車両を取得できます。これを利用して、編成全体の車内放送を一斉に再生する安全な記述パターンです。

```javascript
importPackage(Packages.net.suzumiya.crosstie.api.sound);
var SoundAPI = SoundManager.getInstance();

function doThat(tile, train, first) {
    if (first && train != null) {
        var sounds = ["sound_test:ann_1", "sound_test:ann_2"];
        var delays = [0, 3];
        var length = 20.0; // 車両の長さ
        var maxRadius = 15.0; // 車内放送漏れの最大可聴範囲 (15m)

        var formation = train.getFormation();
        if (formation != null) {
            // 連結編成の場合：全車両に車内放送を一斉に再生
            for (var i = 0; i < formation.size(); i++) {
                var entry = formation.get(i);
                if (entry != null && entry.train != null) {
                    SoundAPI.playInCarAnnouncement(entry.train, length, maxRadius, sounds, delays);
                }
            }
        } else {
            // 単行（1両のみ）の場合：対象の列車のみで再生
            SoundAPI.playInCarAnnouncement(train, length, maxRadius, sounds, delays);
        }
    }
}
```

## 2. 内部仕様と注意点
- **超軽量な線分追従計算**: 20mなどの長い車両の場合、単一の音源では車両の端にいるプレイヤーには音が聞こえなくなってしまいます。CrossTieでは、プレイヤーの現在位置に合わせて音源の座標が「車両の前後軸を示す線分上の最も近い点」へと毎チック瞬時に移動する高度な数学モデルを採用しているため、車両のどこにいても均一に放送が聞こえます。
- **キュー管理**: JSから指定された時間（Tick）差の再生はサーバー側で正確に管理され、時間になった瞬間にクライアントへパケットが送信されます。JS側で `Thread.sleep` などを実行してサーバー全体をフリーズさせる必要は一切ありません。
- **音量減衰と完全遮音特性（駅用スピーカーと同一仕様に統合）**:
  - **車内放送 (`type=0`)**:
    - 車内判定（中心から半径1.5m以内＝車内直径3ブロック）ではドア開閉に関わらず `1.0F` でクリアに聞こえます。
    - ドアがすべて閉まっている状態、または閉まっているドア側（車外）に対しては、音は一切漏れません（**完全遮音：音量 `0.0F`**）。
    - ドアが開いている側（車外）に対しては、基準音量 `0.8F` から指定された `maxRadius` にかけて線形に減衰（駅スピーカーと同等の線形ロジック）しながら聞こえます。
  - **車外スピーカー (`type=1`)**:
    - 車外では、ドアの開閉や左右に関わらず常に車載スピーカーから音声が聞こえます（基準音量 `0.8F` から `maxRadius` にかけて駅と同等の線形減衰）。
    - 車内へは遮音減衰が適用され、ドア開時は `0.5F`、ドア閉時でも `0.1F` でかすかに漏れ聞こえます。
