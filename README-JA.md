<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>YouTube と Bilibili を、どこでも。</h2>
  <h3>セルフホスティングがお好みでない場合は、こちらの今後のオンラインサービスをご覧ください：
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

[English](README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

> [!NOTE]
> 詳細な利用ドキュメントは現在、英語の GitHub Wiki に集約されています。
> この日本語 README は軽量なプロジェクト入口であり、最新の英語ドキュメントより更新が遅れる場合があります。

## PigeonPod とは

PigeonPod は技術志向のユーザー向け self-hosted プロジェクトです。YouTube のチャンネルや再生リスト、Bilibili のコンテンツをポッドキャスト向け RSS に変換し、同期、ダウンロード、管理ルールを自分で制御できます。

特に次のようなユーザーに向いています：

- 自分でサービスをデプロイして運用したい
- YouTube や Bilibili のコンテンツをポッドキャストアプリに取り込みたい
- フィルタ、自动ダウンロード、保持数、保存方式を細かく制御したい

## 主要機能

- **🎯 スマートサブスクリプションとプレビュー**: YouTube や Bilibili のチャンネルと再生リストを数秒で購読できます。
- **📻 どのクライアントでも使える安全な RSS**: あらゆるポッドキャストアプリ向けに保護付き標準 RSS を生成します。
- **🎦 柔軟な音声／動画出力**: 音声または動画でダウンロードし、品質と形式を調整できます。
- **🤖 自動同期と履歴取得**: 購読を自動で最新化し、必要に応じて過去動画も取得できます。
- **🍪 拡張された Cookie サポート**: YouTube と Bilibili の Cookie を使って制限付きコンテンツへ安定してアクセスできます。
- **🌍 プロキシ対応のネットワーク接続**: YouTube API と yt-dlp の通信をカスタムプロキシ経由でルーティングできます。
- **🔗 エピソードのワンクリック共有**: ログイン不要で再生できる公開ページ付きで各エピソードを共有できます。
- **📦 高速な一括ダウンロード**: 大量の過去エピソードも検索・選択・キュー追加を効率よく行えます。
- **📊 ダウンロードダッシュボードと一括操作**: タスク状況を追跡し、再試行・キャンセル・削除をまとめて実行できます。
- **🔔 ダウンロード失敗の要約と通知**: 自動リトライが尽きたときに、メールまたは Webhook で失敗タスクの要約を受け取れます。
- **🔍 フィード単位のフィルタと保持ポリシー**: キーワード、再生時間、件数制限で同期範囲をコントロールできます。
- **⏱ 新着エピソードのより賢いダウンロード**: 自動ダウンロードを遅らせて新着動画の処理精度を高めます。
- **🎛 カスタマイズ可能なフィードと内蔵プレイヤー**: タイトルやカバーを調整し、そのまま Web 上で再生できます。
- **🧩 エピソード管理と細かな制御**: ダウンロード、再試行、キャンセル、削除と同時にファイルも整理します。
- **🔓 信頼できる環境での自動ログイン**: 信頼できるアクセス制御の背後では手動ログインを省略できます。
- **📈 YouTube API 使用状況の可視化**: 同期が上限に達する前にクォータ消費を把握できます。
- **🔄 OPML 購読エクスポート**: 購読を簡単に書き出し、他のポッドキャストクライアントへ移行できます。
- **⬆️ アプリ内 yt-dlp 管理**: ランタイムの管理、使用中バージョンの切り替え、yt-dlp の更新をアプリ内で行えます。
- **🛠 高度な yt-dlp 引数設定**: カスタム yt-dlp 引数でダウンロード動作を細かく調整できます。
- **📚 Podcasting 2.0 チャプター対応**: チャプターファイルを生成し、より豊かな再生ナビゲーションを実現します。
- **🌐 多言語対応のレスポンシブ UI**: 8 言語の UI で、デスクトップでもモバイルでも快適に使えます。

## クイックスタート

推奨される導入方法は Docker Compose です：

```yml
version: '3.9'
services:
  pigeon-pod:
    image: 'ghcr.io/aizhimou/pigeon-pod:latest'
    restart: unless-stopped
    container_name: pigeon-pod
    ports:
      - '8834:8080'
    environment:
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db
      # オプション: 別の認証レイヤーで Web UI を保護している場合のみ内蔵認証を無効化
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

起動：

```bash
docker compose up -d
```

アクセス先：

```text
http://localhost:8834
```

デフォルト認証情報：

- ユーザー名: `root`
- パスワード: `Root@123`

> [!WARNING]
> `PIGEON_AUTH_ENABLED` のデフォルト値は `true` です。auth proxy、リバースプロキシのアクセス制御、VPN、またはプライベートネットワークなど、別の信頼できる保護レイヤーが Web UI を守っている場合にのみ `false` に設定してください。
>
> 認証を無効にしたインスタンスをインターネットへ直接公開しないでください。

## ドキュメント

公式の利用者向けドキュメントは英語の GitHub Wiki です：

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)

## よく使うリンク

- [英語版メイン README](README.md)
- [GitHub Wiki](https://github.com/aizhimou/PigeonPod/wiki)
- [Releases](https://github.com/aizhimou/PigeonPod/releases)
- [Issues](https://github.com/aizhimou/PigeonPod/issues)

## 補足

- 現在の推奨デプロイ方法は Docker であり、JAR の直接実行は推奨されません。
- プロジェクトが自分に合っているかを素早く判断したい場合は、この README と英語 Wiki を見れば十分です。
- より深いカスタマイズ、開発、アーキテクチャ理解が必要な場合は、リポジトリ内の `dev-docs/` を参照してください。

---

<div align="center">
  <p>ポッドキャスト好きのために ❤️ を込めて制作！</p>
  <p>⭐ PigeonPod が気に入ったら GitHub で Star をお願いします。</p>
</div>
