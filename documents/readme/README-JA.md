<div align="center">
  <img src="../../documents/assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  
  <h2>YouTube と Bilibili を、どこでも。</h2>
  <h3>セルフホスティングがお好みでない場合は、こちらの今後のオンラインサービスをご覧ください：
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[English](../../README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

## スクリーンショット

![index-dark&light](documents/assets/screenshots/home-27-11-2025.png)
<div align="center">
  <p style="color: gray">チャンネル一覧</p>
</div>

![detail-dark&light](documents/assets/screenshots/feed-27-11-2025.png)
<div align="center">
  <p style="color: gray">チャンネル詳細</p>
</div>

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
- **🔍 フィード単位のフィルタと保持ポリシー**: キーワード、再生時間、件数制限で同期範囲をコントロールできます。
- **⏱ 新着エピソードのより賢いダウンロード**: 自動ダウンロードを遅らせて新着動画の処理精度を高めます。
- **🎛 カスタマイズ可能なフィードと内蔵プレイヤー**: タイトルやカバーを調整し、そのまま Web 上で再生できます。
- **🧩 エピソード管理と細かな制御**: ダウンロード、再試行、キャンセル、削除と同時にファイルも整理します。
- **🔓 信頼できる環境での自動ログイン**: 信頼できるアクセス制御の背後では手動ログインを省略できます。
- **📈 YouTube API 使用状況の可視化**: 同期が上限に達する前にクォータ消費を把握できます。
- **🔄 OPML 購読エクスポート**: 購読を簡単に書き出し、他のポッドキャストクライアントへ移行できます。
- **⬆️ アプリ内 yt-dlp 更新**: アプリを離れずに yt-dlp を更新できます。
- **🛠 高度な yt-dlp 引数設定**: カスタム yt-dlp 引数でダウンロード動作を細かく調整できます。
- **📚 Podcasting 2.0 チャプター対応**: チャプターファイルを生成し、より豊かな再生ナビゲーションを実現します。
- **🌐 多言語対応のレスポンシブ UI**: 8 言語の UI で、デスクトップでもモバイルでも快適に使えます。

## デプロイメント

### Docker Composeを使用（推奨）

**お使いのマシンにDockerとDocker Composeがインストールされていることを確認してください。**

1. docker-compose設定ファイルを使用し、必要に応じて環境変数を変更します：
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
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db # set to your database path
      # オプション: 別の認証レイヤーで Web UI を保護している場合のみ内蔵認証を無効化
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

> [!WARNING]
> `PIGEON_AUTH_ENABLED` のデフォルト値は `true` です。auth proxy、リバースプロキシのアクセス制御、VPN、またはプライベートネットワークなど、別の信頼できる保護レイヤーが Web UI を守っている場合にのみ `false` に設定してください。
>
> 内蔵認証を無効にする場合は、他の方法で必ず PigeonPod を保護してください。認証を無効にしたインスタンスをインターネットへ直接公開しないでください。

2. サービスを開始：
```bash
docker-compose up -d
```

3. アプリケーションにアクセス：
ブラウザで `http://localhost:8834` にアクセスし、**デフォルトユーザー名: `root`、デフォルトパスワード: `Root@123`** でログイン

### JARで実行

**お使いのマシンにJava 17+とyt-dlpがインストールされていることを確認してください。**

1. [Releases](https://github.com/aizhimou/pigeon-pod/releases)から最新版のJARをダウンロード

2. JARファイルと同じディレクトリにdataディレクトリを作成：
```bash
mkdir -p data
```

3. アプリケーションを実行：
```bash
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # データベースのパスを設定
           pigeon-pod-x.x.x.jar
```

4. アプリケーションにアクセス：
ブラウザで `http://localhost:8080` にアクセスし、**デフォルトユーザー名: `root`、デフォルトパスワード: `Root@123`** でログイン

## Storage Configuration

- PigeonPod supports `LOCAL` and `S3` storage modes.
- You can only enable one mode at a time.
- S3 mode supports MinIO, Cloudflare R2, AWS S3, and other S3-compatible services.
- Switching storage mode does not migrate historical media automatically. You must migrate files manually.

### Storage Quick Comparison

| Mode | Pros | Cons |
| --- | --- | --- |
| `LOCAL` | Easy setup, no external dependency | Uses local disk, harder to scale |
| `S3` | Better scalability, suitable for cloud deployment | Requires object storage setup and credentials |

## ドキュメント

- [YouTube APIキーの取得方法](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [YouTubeクッキーの設定方法](../youtube-cookie-setup/youtube-cookie-setup-en.md)
- [YouTubeチャンネルIDの取得方法](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)

## 技術スタック

### バックエンド
- **Java 17** - コア言語
- **Spring Boot 3.5** - アプリケーションフレームワーク
- **MyBatis-Plus 3.5** - ORMフレームワーク
- **Sa-Token** - 認証フレームワーク
- **SQLite** - 軽量データベース
- **Flyway** - データベースマイグレーションツール
- **YouTube Data API v3** - YouTubeデータ取得
- **yt-dlp** - 動画ダウンロードツール
- **Rome** - RSS生成ライブラリ

### フロントエンド
- **Javascript (ES2024)** - コア言語
- **React 19** - アプリケーションフレームワーク
- **Vite 7** - ビルドツール
- **Mantine 8** - UIコンポーネントライブラリ
- **i18next** - 国際化サポート
- **Axios** - HTTPクライアント

## 開発ガイド

### 環境要件
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### ローカル開発

1. プロジェクトをクローン：
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. データベースの設定：
```bash
# データディレクトリを作成
mkdir -p data/audio

# データベースファイルは初回起動時に自動的に作成されます
```

3. YouTube APIの設定：
   - [Google Cloud Console](https://console.cloud.google.com/)でプロジェクトを作成
   - YouTube Data API v3を有効化
   - APIキーを作成
   - ユーザー設定でAPIキーを設定

4. バックエンドの開始：
```bash
cd backend
mvn spring-boot:run
```

5. フロントエンドの開始（新しいターミナル）：
```bash
cd frontend
npm install
npm run dev
```

6. アプリケーションにアクセス：
- フロントエンド開発サーバー: `http://localhost:5173`
- バックエンドAPI: `http://localhost:8080`

### 開発上の注意点
1. yt-dlpがインストールされ、コマンドラインで利用可能であることを確認
2. 正しいYouTube APIキーを設定
3. オーディオ保存ディレクトリに十分なディスク容量があることを確認
4. 定期的に古いオーディオファイルを削除してスペースを節約

---

<div align="center">
  <p>ポッドキャスト愛好者のために❤️で作成</p>
  <p>⭐ PigeonPodが気に入ったら、GitHubでスターをお願いします！</p>
</div>
