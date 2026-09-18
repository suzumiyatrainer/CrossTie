# 材質上書きAPI (CrossTieMaterialUtils)

`CrossTieMaterialUtils` は、スクリプト（JavaScriptなど）から動的に車両（Entity）の特定材質（マテリアル）のテクスチャを上書き、または元に戻すためのAPIを提供します。
通常の静的画像（PNG等）のほか、GIFアニメーション（.gif）の指定もサポートしています。

## スクリプトでのインポート方法

このAPIを使用するには、スクリプトの先頭で以下のようにパッケージをインポートします。

```javascript
importPackage(Packages.net.suzumiya.crosstie.utils);
```

## 提供されているメソッド

### 1. テクスチャの上書き (`setTexture`)

指定した車両の特定の材質のテクスチャを、指定したパスのテクスチャで上書きします。

```javascript
CrossTieMaterialUtils.setTexture(entity, targetMat, path);
```

- **`entity`** (Entity): 対象となる車両のエンティティオブジェクト。render/serverではentityと指定しないとクラッシュする。
- **`targetMat`** (int): 上書き対象の材質ID（0〜255）。JSONモデルで定義されている材質のインデックスに対応します。
- **`path`** (String): 適用するテクスチャのパス（例: `"textures/blocks/stone.png"` や 独自のリソースパス）。パスの末尾が `.gif` の場合、GIFアニメーションとしてロードおよび再生されます。

### 2. テクスチャの初期化・リセット (`resetTexture`)

テクスチャの上書きを解除し、JSONモデルで指定されているデフォルトのテクスチャに戻します。

```javascript
CrossTieMaterialUtils.resetTexture(entity, targetMat);
```

- **`entity`** (Entity): 対象となる車両のエンティティオブジェクト。
- **`targetMat`** (int): リセット対象の材質ID（0〜255）。

### 3. スクリプト手動描画パーツへのテクスチャ適用 (`bindTexture`)

スクリプト内で `Parts` を独自に生成し、手動で `.render(renderer)` を呼び出して描画する場合に使用します。
指定した材質IDに上書きテクスチャが設定されていればそれを適用し、設定されていなければデフォルトのテクスチャを適用します。

```javascript
CrossTieMaterialUtils.bindTexture(entity, targetMat, defaultPath);
```

- **`entity`** (Entity): 対象となる車両のエンティティオブジェクト。
- **`targetMat`** (int): 適用したい上書き設定の材質ID（0〜255）。
- **`defaultPath`** (String): 上書き設定が存在しなかった場合にバインドする、デフォルトのテクスチャパス。

**使用例:**
```javascript
GL11.glPushMatrix();
// 5番マテリアル（ROM等）の上書き設定があればバインド、無ければデフォルトのROM画像をバインド
CrossTieMaterialUtils.bindTexture(entity, 5, "textures/train/suzu_tx3000/hokomaku/ROM_TX3000.png");

// 自分で指定したパーツを描画
type_00.render(renderer);
GL11.glPopMatrix();
```

## 内部的な動作とパフォーマンス

- 内部では `CrossTieTextureOverrideManager` が状態を管理しています。
- 描画ループでのパフォーマンス低下（オートボクシング等）を防ぐため、FastUtil（`Int2ObjectMap`, `Byte2ObjectMap`）を使用して軽量に状態を保持しています。
- `setTexture` を呼び出した際、現在適用されているテクスチャパスと同一のパスを指定した場合は、不要な再読み込み処理をスキップ（早期リターン）する最適化が行われています。
- `resetTexture` を呼び出すとオーバーライド用のマップから該当材質のエントリが削除され、元のデフォルトテクスチャでの描画にフォールバックします。
