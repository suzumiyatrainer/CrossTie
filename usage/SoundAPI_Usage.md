# CrossTie Sound API 使い方・連携仕様書

このドキュメントでは、CrossTieの非破壊・超軽量な音響API（`SoundManager`）の実際の使い方と、ATSAssistMod等のJavaScript（Nashorn）から連携する方法を解説します。

## 1. JavaScript側（ATSAssistMod）からの呼び出し方

ATSAssistModでは、地上子を踏んだ際に実行されるJSスクリプトのコンテキスト内に、あらかじめ `SoundAPI` という変数名で `SoundManager` のシングルトンインスタンスがバインドされています。

### 1.1 車内放送・車外スピーカーAPI
車両のドア状態や立ち位置に応じて音量がリアルタイムに変化する、非常に高度な追従型サウンドを再生します。

```javascript
/**
 * @param {EntityTrainBase} train - 対象の列車インスタンス
 * @param {float} length - 車両の長さ（16.0, 18.0, 20.0 等）
 * @param {String[]} sounds - 再生するサウンド名の配列
 * @param {int[]} delaySeconds - 各サウンドの再生遅延（**秒単位**。例: 0なら即時、3なら3秒後）
 */

function doThat(tile, train, first) {
    // Nashornから渡されるJSの生配列（[]）を、API内部で自動的にJava配列へパースします。
    // Java.to(...) の記述は一切不要です！
    var sounds = ["sound_test:xxx.soundfileN", "sound_test:xxx.soundfileN2"];
    var delays = [0, 3]; // ピンポンを即時(0)、アナウンスを3秒後(3)に再生

    // 1. 車内放送の再生
    // （ドア閉時は車外に漏れず、ドア開時は3〜9ブロックで減衰して漏れる）
    SoundAPI.playInCarAnnouncement(train, 20.0, sounds, delays);

    // 2. 車外スピーカーの再生
    // （開いているドア側、または前回開いていたドア側に向かって再生される）
    var extSounds = ["sound_test:xxx.soundfileN3"];
    var extDelays = [0];
    SoundAPI.playExteriorSound(train, 20.0, extSounds, extDelays);
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

## 2. 内部仕様と注意点
- **超軽量な線分追従計算**: 20mなどの長い車両の場合、単一の音源では車両の端にいるプレイヤーには音が聞こえなくなってしまいます。CrossTieでは、プレイヤーの現在位置に合わせて音源の座標が「車両の前後軸を示す線分上の最も近い点」へと毎チック瞬時に移動する高度な数学モデルを採用しているため、車両のどこにいても均一に放送が聞こえます。
- **キュー管理**: JSから指定された時間（Tick）差の再生はサーバー側で正確に管理され、時間になった瞬間にクライアントへパケットが送信されます。JS側で `Thread.sleep` などを実行してサーバー全体をフリーズさせる必要は一切ありません。
