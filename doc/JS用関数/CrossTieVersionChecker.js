// ============================================================
// CrossTie 連携: 存在確認 & バージョンチェック (グローバル関数)
// ============================================================
var CROSSTIE_REQUIRED_VERSION = "1.0.0-Alpha13"; // このスクリプトが要求するCrossTieの最低バージョン(接頭辞なし)

var _crossTieChecked = false;
var _crossTieAvailable = false;
var CrossTieVersionChecker = null;

/**
 * CrossTieが利用可能か判定するグローバル関数。
 * どの関数からも suzu_isCrossTieAvailable() の形で呼べる。
 * 判定結果はスクリプト内で1回だけ計算してキャッシュするので、
 * 毎tick/毎フレーム Java.type や isAtLeast を呼び直すコストは発生しない。
 */
function suzu_isCrossTieAvailable() {
    if (_crossTieChecked) return _crossTieAvailable;
    _crossTieChecked = true;

    try {
        // 必ず完全修飾名の Java.type() で判定する。
        // importPackage(...) は対象パッケージが無くても例外を投げないため、
        // 存在確認には使えない(バレ名参照時に初めてReferenceErrorになるだけ)。
        CrossTieVersionChecker = Java.type("net.suzumiya.crosstie.utils.js.CrossTieVersionChecker");

        if (CrossTieVersionChecker.exists() && CrossTieVersionChecker.isAtLeast(CROSSTIE_REQUIRED_VERSION)) {
            _crossTieAvailable = true;
            java.lang.System.out.println("[CrossTie連携] " + CrossTieVersionChecker.getVersion() + " を検出。高速化パスを使用します。");
        } else {
            java.lang.System.out.println("[CrossTie連携] 要求バージョン(" + CROSSTIE_REQUIRED_VERSION + ")未満のため、純JS実装にフォールバックします。");
        }
    } catch (e) {
        // CrossTie未導入 or 旧verでexists()/isAtLeast()自体が無い場合もここに来る
        java.lang.System.out.println("[CrossTie連携] 未検出のため、純JS実装にフォールバックします。");
    }

    return _crossTieAvailable;
}