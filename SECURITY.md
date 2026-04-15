# Security Policy

## Reporting a Vulnerability

セキュリティ脆弱性を発見された場合は、GitHubの[Security Advisory](https://github.com/suzumiyatrainer/CrossTie/security/advisories)機能を使用して報告してください。

**公開報告は避けてください** - 脆弱性は機密を持って報告してください。

### 報告手順

1. GitHub リポジトリの **Security** タブにアクセス
2. **Report a vulnerability** をクリック
3. 脆弱性の詳細を記入
   - 脆弱性の説明
   - 影響を受けるバージョン
   - 重大度（Critical, High, Medium, Low）
   - 再現手順（もしあれば）

### 対応予定

- **高重大度（Critical）**: 7日以内に対応開始
- **中程度（Medium）**: 14日以内に対応開始
- **低重大度（Low）**: 30日以内に対応開始

## セキュリティベストプラクティス

### Code Review
- すべてのPull Requestにはレビューが必須です
- Masterブランチへの直接プッシュは禁止されています

### Dependencies
- Dependabotが依存関係の脆弱性を自動で検出します
- 定期的なセキュリティアップデートの確認と適用が必要です

### Secrets Management
- API キー、パスワード、トークンなどをコードに含めないでください
- `.env.example` に構造を示し、実際の秘密は `.env` に保存してください

### Branch Protection
- Force pushは禁止されています
- ブランチ削除には追加の確認が必要です

## セキュリティ関連リンク

- [GitHub Security Best Practices](https://docs.github.com/en/code-security)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
