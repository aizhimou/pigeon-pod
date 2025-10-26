<div align="center">
  <img src="../../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>お気に入りのYouTubeチャンネルを、最もシンプルでエレガントな方法でポッドキャストチャンネルに変換します。</h2>
  <h3>セルフホスティングがお好みでない場合は、こちらの今後のオンラインサービスをご覧ください：
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](README-PT.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](README-DE.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](README-KO.md)
</div>

## スクリーンショット

![index-dark&light](../assets/screenshots/index-dark&light.png)
<div align="center">
  <p style="color: gray">チャンネル一覧</p>
</div>

![detail-dark&light](../assets/screenshots/detail-dark&light.png)
<div align="center">
  <p style="color: gray">チャンネル詳細</p>
</div>

## 主要機能

- **🎯 スマートサブスクリプション**: YouTube のチャンネルや再生リストをワンクリックで追加・同期。
- **🤖 自動同期更新**: 最新のチャンネルコンテンツを自動的にチェックし、増分更新で同期。
- **📻 RSSポッドキャスト登録**: 標準的なRSS登録リンクを生成し、あらゆるポッドキャストクライアントと互換性。
- **🔍 コンテンツフィルタリング**: タイトル・説明のキーワード（包含/除外）およびエピソード長でのフィルタリングに対応。
- **📊 エピソード管理**: エピソードの表示、削除、ダウンロード失敗時の再試行。
- **🎦 ビデオ対応**: ビデオエピソードのダウンロードに対応。解像度やエンコードを選択でき、動画対応のポッドキャストクライアントで視聴可能。
- **🎚 音質コントロール**: 音質レベル0〜10を選ぶか元のトラックを維持して、音質とファイルサイズのバランスを調整。
- **💽 チャプターとメタデータ**: ファイルにメタデータとチャプターマーカーを自動書き込みし、リスニング体験を向上。
- **✨ 広告なし再生**: エピソードから導入部および中間の広告を自動的に削除。
- **🍪 カスタムクッキー**: クッキーをアップロードすることで年齢制限コンテンツやメンバー限定コンテンツの登録をサポート。
- **🌐 多言語サポート**: 英語、中国語、スペイン語、ポルトガル語、日本語、フランス語、ドイツ語、韓国語のインターフェースを完全サポート。
- **📱 レスポンシブUI**: あらゆるデバイスで、いつでもどこでも優れた体験を提供。

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
      - 'PIGEON_BASE_URL=https://pigeonpod.cloud' # お使いのドメインに設定
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # オーディオファイルのパスを設定
      - 'PIGEON_COVER_FILE_PATH=/data/cover/' # カバーファイルのパスを設定する
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # データベースのパスを設定
    volumes:
      - data:/data

volumes:
  data:
```

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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # お使いのドメインに設定
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # オーディオファイルのパスを設定
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # データベースのパスを設定
           pigeon-pod-x.x.x.jar
```

4. アプリケーションにアクセス：
ブラウザで `http://localhost:8080` にアクセスし、**デフォルトユーザー名: `root`、デフォルトパスワード: `Root@123`** でログイン

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

### プロジェクト構成
```
pigeon-pod/
├── backend/                 # Spring Bootバックエンド
│   ├── src/main/java/      # Javaソースコード
│   │   └── top/asimov/pigeon/
│   │       ├── controller/ # REST APIコントローラー
│   │       ├── service/    # ビジネスロジックサービス
│   │       ├── mapper/     # データアクセス層
│   │       ├── model/      # データモデル
│   │       ├── scheduler/  # スケジュールタスク
│   │       └── worker/     # 非同期ワーカー
│   └── src/main/resources/ # 設定ファイル
├── frontend/               # Reactフロントエンド
│   ├── src/
│   │   ├── components/     # 再利用可能なコンポーネント
│   │   ├── pages/         # ページコンポーネント
│   │   ├── context/       # React Context
│   │   └── helpers/       # ユーティリティ関数
│   └── public/            # 静的アセット
├── data/                  # データ保存ディレクトリ
│   ├── audio/            # オーディオファイル
│   └── pigeon-pod.db     # SQLiteデータベース
├── docker-compose.yml    # Dockerオーケストレーション設定
└── Dockerfile           # Dockerイメージビルド
```

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
