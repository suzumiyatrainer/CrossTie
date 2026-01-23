# CrossTie - RTM最適化Mod for Minecraft 1.7.10

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.7.10-green)
![Forge](https://img.shields.io/badge/Forge-10.13.4.1614-blue)
![License](https://img.shields.io/badge/license-MIT-blue)

Minecraft 1.7.10のRealTrainMod (RTM/KaizPatchX)及び関連Modに対して、**TPS（サーバー処理）とFPS（クライアント描画）の両面から最適化を提供する**Modです。

## 特徴

### 🚄 RTM/KaizPatchX TPS最適化
- **静止列車の更新スキップ**: 完全に停止している列車の不要な計算を削減
- **チャンク更新頻度削減**: チャンクローダーの更新を5tickに1回に削減（80%の削減）
- **アニメーション計算の最適化**: サーバー側での不要なアニメーション計算を無効化

### 🎨 RTM/KaizPatchX FPS最適化
- **遠距離車両カリング**: 200ブロック以上離れた車両の描画をスキップ
- **ライトエフェクト距離制限**: 64ブロック以上離れた車両のボリュームライトを無効化
- **LODシステム（予定）**: 距離に応じた描画品質の自動調整

### 🔧 モジュラー設計
 **存在しないModへの最適化は自動的に無効化** されます:
- RTM/KaizPatchX
- 竹Mod（予定）
- OEMod（予定）
- ATSAssistMod（予定）

### 🌐 広範な互換性
以下の軽量化Mod環境との共存を目指します:
- **OptiFine + FastCraft**
- **FalseTweaks + Beddium + SwanSong**
- **Angelica** (Sodium 1.7.10 port)

### ⚙️ サーバー/クライアント両対応
サーバー専用・クライアント専用のどちらの環境でも動作可能です。

---

## 必須前提Mod

| Mod | バージョン | 説明 |
|-----|----------|------|
| **Minecraft Forge** | 10.13.4.1614+ | Modローダー |
| **UniMixins** | 0.2.0+ | Late Mixin support |

## 推奨Mod

最適化効果を得られるMod:
- RTM (RealTrainMod) / KaizPatchX
- 竹Mod
- OEMod

---

## インストール方法

1. **Minecraft Forge 1.7.10をインストール**
   - [Forge公式サイト](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)からダウンロード

2. **UniMixinsをインストール**
   - [UniMixins Releases](https://github.com/LegacyModdingMC/UniMixins/releases)からダウンロード
   - `mods/`フォルダに配置

3. **CrossTieをインストール**
   - 本Modのjarファイルを`mods/`フォルダに配置

4. **Minecraftを起動**
   - ログで`CrossTie Mod Detection`が表示されることを確認

---

## 設定

（現在、設定ファイルは未実装）

将来的に以下の設定が追加予定:
- チャンク更新間隔の調整
- 最大描画距離の調整
- ライトエフェクト距離の調整
- 各最適化機能のON/OFF

---

## 開発状況

### ✅ Phase 1: プロジェクトセットアップ（完了）
- UniMixins統合
- Mod検出システム
- 基本的なRTM TPS/FPS最適化

### 🔧 Phase 2: RTM TPS最適化（進行中）
- EntityBogie最適化
- Formation（編成）システム最適化

### 🔜 Phase 3: RTM FPS最適化
- バッチレンダリング
- LODシステム

### 🔜 Phase 4-7: 互換性・他Mod・テスト
- OptiFine/Angelica/FalseTweaks互換性
- Bamboo/OEMod最適化
- パフォーマンステスト

---

## パフォーマンス改善効果（予測）

### TPS改善
- 静止列車が多い環境: **10-20%のTPS改善**
- 大規模編成運用: **15-30%のTPS改善**

### FPS改善
- 多数の車両が視界内: **20-40%のFPS改善**
- ライトエフェクト有効時: **30-50%のFPS改善**

（実測値は今後のテストで公開予定）

---

## トラブルシューティング

### Q: クラッシュする
**A**: 以下を確認してください:
1. UniMixinsがインストールされているか
2. Minecraft Forge 10.13.4.1614以上を使用しているか
3. クラッシュログを[Issues](../../issues)に報告してください

### Q: 効果が感じられない
**A**: 
- RTM/KaizPatchXがインストールされているか確認
- F3デバッグ画面でTPS/FPSを確認
- 列車が多数走行している環境で効果が顕著です

### Q: OptiFineと競合する
**A**: 
- 現在、OptiFine互換性レイヤーは開発中です
- 問題が発生した場合は[Issues](../../issues)に報告してください

---

## 技術詳細

### 使用技術
- **UniMixins**: Late Mixin technology
- **RetroFuturaGradle**: Gradle build system
- **SpongePowered Mixin**: バイトコード操作

### 開発環境のセットアップ

**必須: Java 8 (JDK 1.8)**
※ Gradle 7.6.4を使用しているため、Java 17は必須ではありません。

```bash
# 1. クローン
git clone <repository-url>
cd CrossTie

# 2. ワークスペースセットアップ (初回のみ時間がかかります)
./gradlew clean setupDecompWorkspace

# 3. IDE設定
./gradlew idea    # for IntelliJ
# または
./gradlew eclipse # for Eclipse
```

#### TPS最適化
- `jp.ngt.rtm.entity.train.EntityTrainBase`
  - `updateSpeed()` - 速度計算最適化
  - `onVehicleUpdate()` - チャンク更新最適化
  - `updateAnimation()` - アニメーション最適化

#### FPS最適化
- `jp.ngt.rtm.entity.vehicle.RenderVehicleBase`
  - `renderVehicleBase()` - 距離カリング
  - `renderLightEffect()` - ライト距離制限

---

## コントリビューション

プルリクエスト・Issue報告を歓迎します！

### 開発環境のセットアップ

```bash
git clone <repository-url>
cd CrossTie
./gradlew setupDecompWorkspace
./gradlew idea  # IntelliJ IDEA用 または ./gradlew eclipse
```

### ビルド方法

```bash
./gradlew build
```

生成されたjarファイルは `build/libs/` に出力されます。

---

## ライセンス

MIT License

Copyright (c) 2026 SuzumiyaTrainer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## クレジット

- **RTM/KaizPatchX**: [KaizJP](https://github.com/Kai-Z-JP) - 解析・最適化対象
- **UniMixins**: [LegacyModdingMC](https://github.com/LegacyModdingMC/UniMixins) - Mixin framework
- **GTNH**: [GTNewHorizons](https://github.com/GTNewHorizons) - Build template

---

## リンク

- [UniMixins GitHub](https://github.com/LegacyModdingMC/UniMixins)
- [本Modの実装計画](../../implementation_plan.md)
- [タスク管理](../../task.md)

---

**RTMをもっと快適に、もっとスムーズに。CrossTieと共に。**
