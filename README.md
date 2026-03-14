<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>Listen to YouTube & Bilibili, Anywhere, Anytime.</h2>
  <h3>If self-hosting isn't your thing, take a look at our upcoming online services:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

  [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

## Screenshots

![index-dark&light](.github/docs-assets/home-27-11-2025.png)
<div align="center">
  <p style="color: gray">Channel list</p>
</div>

![detail-dark&light](.github/docs-assets/feed-27-11-2025.png)
<div align="center">
  <p style="color: gray">Channel detail</p>
</div>

## Core Features

- **🎯 Smart Subscription & Preview**: Subscribe to YouTube or Bilibili channels and playlists in seconds.
- **📻 Secure RSS for Any Client**: Generate protected standard RSS feeds for any podcast app.
- **🎦 Flexible Audio/Video Output**: Download as audio or video with quality and format control.
- **🤖 Auto Sync & History Backfill**: Keep subscriptions updated and backfill older videos on demand.
- **🍪 Expanded Cookie Support**: Use YouTube and Bilibili cookies for more reliable restricted-content access.
- **🌍 Proxy-ready Network Access**: Route YouTube API and yt-dlp traffic through custom proxies.
- **🔗 One-click Episode Sharing**: Share any episode with a public page for direct playback without login.
- **📦 Fast Batch Downloads**: Search, select, and queue large back catalogs efficiently.
- **📊 Download Dashboard & Bulk Actions**: Track task status and retry, cancel, or delete in bulk.
- **🔍 Per-feed Filters & Retention**: Control sync scope with keywords, duration, and episode limits.
- **⏱ Smarter New Episode Downloads**: Delay auto-downloads to improve fresh-video processing results.
- **🎛 Customizable Feeds & Player**: Customize titles and cover art, then play episodes on the web.
- **🧩 Episode Management & Control**: Download, retry, cancel, and delete episodes with file cleanup.
- **🔓 Trusted-environment Auto Login**: Skip manual sign-in when PigeonPod runs behind trusted access controls.
- **📈 YouTube API Usage Insights**: Monitor quota usage before sync jobs hit the limit.
- **🔄 OPML Subscription Export**: Export subscriptions for easy migration between podcast clients.
- **⬆️ In-app yt-dlp Updates**: Update yt-dlp without leaving the app.
- **🛠 Advanced yt-dlp Arguments**: Fine-tune downloads with custom yt-dlp arguments.
- **📚 Podcasting 2.0 Chapters Support**: Generate chapter files for richer player navigation.
- **🌐 Multilingual Responsive UI**: Use PigeonPod across devices in eight interface languages.

## Deployment

### Using Docker Compose (Recommended)

**Make sure you have Docker and Docker Compose installed on your machine.**

1. Use the docker-compose configuration file, modify environment variables according to your needs
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
      # Optional: disable PigeonPod built-in auth when running behind another auth layer
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

> [!WARNING]
> `PIGEON_AUTH_ENABLED` defaults to `true`. Set it to `false` only if another trusted layer already protects the web UI, such as an auth proxy, reverse proxy access control, VPN, or private network.
>
> If you disable built-in auth, you must secure PigeonPod by other means. Do not expose an auth-disabled instance directly to the public Internet.

2. Start the service
```bash
docker-compose up -d
```

3. Access the application
Open your browser and visit `http://localhost:8834` with **default username: `root` and default password: `Root@123`**

### Run with JAR

**Make sure you have Java 17+ and yt-dlp installed on your machine.**

1. Download the latest release JAR from [Releases](https://github.com/aizhimou/pigeon-pod/releases)

2. Create data directory in the same directory as the JAR file.
```bash
mkdir -p data
```

3. Run the application
```bash
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # set to your database path
           pigeon-pod-x.x.x.jar
```

4. Access the application
Open your browser and visit `http://localhost:8080` with **default username: `root` and default password: `Root@123`**


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


## Documentation

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)


## Tech Stack

### Backend
- **Java 17** - Core language
- **Spring Boot 3.5** - Application framework
- **MyBatis-Plus 3.5** - ORM framework
- **Sa-Token** - Authentication framework
- **SQLite** - Lightweight database
- **Flyway** - Database migration tool
- **YouTube Data API v3** - YouTube data retrieval
- **yt-dlp** - Video download tool
- **Rome** - RSS generation library

### Frontend
- **Javascript (ES2024)** - Core language
- **React 19** - Application framework
- **Vite 7** - Build tool
- **Mantine 8** - UI component library
- **i18next** - Internationalization support
- **Axios** - HTTP client

## Development Guide

### Environment Requirements
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### Local Development

1. Clone the project
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. Configure database
```bash
# Create data directory
mkdir -p data/audio

# Database file will be created automatically on first startup
```

3. Configure YouTube API
   - Create a project in [Google Cloud Console](https://console.cloud.google.com/)
   - Enable YouTube Data API v3
   - Create an API key
   - Configure the API key in user settings

4. Start backend
```bash
cd backend
mvn spring-boot:run
```

5. Start frontend (new terminal)
```bash
cd frontend
npm install
npm run dev
```

6. Access the application
- Frontend dev server: `http://localhost:5173`
- Backend API: `http://localhost:8080`

### Development Notes

1. Ensure yt-dlp is installed and available in command line
2. Configure correct YouTube API key
3. Ensure audio storage directory has sufficient disk space
4. Regularly clean up old audio files to save space

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=aizhimou/pigeon-pod&type=Timeline)](https://www.star-history.com/#aizhimou/pigeon-pod&Timeline)
---

<div align="center">
  <p>Made with ❤️ for podcast enthusiasts!</p>
  <p>⭐ If you like PigeonPod, give us a star on GitHub!</p>
</div>
