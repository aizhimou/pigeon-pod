<div align="center">
  <img src="frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Listen to YouTube. Anywhere.</h2>
  <h3>If self-hosting isn't your thing, take a look at our upcoming online services:
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">

  [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](documents/readme/README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](documents/readme/README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](documents/readme/README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](documents/readme/README-JA.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](documents/readme/README-DE.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](documents/readme/README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](documents/readme/README-KO.md)
</div>

## Screenshots

![index-dark&light](documents/assets/screenshots/home-27-11-2025.png)
<div align="center">
  <p style="color: gray">Channel list</p>
</div>

![detail-dark&light](documents/assets/screenshots/feed-27-11-2025.png)
<div align="center">
  <p style="color: gray">Channel detail</p>
</div>

## Core Features

- **🎯 Smart Subscription & Preview**: Paste any YouTube channel or playlist URL, auto-detect its type, and preview feed details before subscribing.
- **🤖 Auto Sync & History Backfill**: Periodically sync new uploads in the background, with configurable initial episodes and one-click backfill of historical videos.
- **⏱ Delayed Auto Download for New Episodes**: Configure per-feed auto-download delay windows to improve `--sponsorblock` success rates on newly published videos.
- **📻 Secure RSS for Any Client**: Generate standard, API-key-protected RSS links for channels and playlists that work in all podcast apps.
- **🔄 OPML Subscription Export**: Export all subscriptions as a standard OPML file for smooth migration between podcast clients.
- **🔍 Per-feed Filters & Retention**: Filter episodes by title/description keywords (include/exclude), minimum duration, and set per-feed sync and maximum episode limits.
- **📊 Download Dashboard & Bulk Actions**: Real-time dashboard for pending/downloading/completed/failed tasks, with detailed error logs and one-click bulk cancel/delete/retry.
- **🧩 Episode Management & Control**: Infinite-scrolling episode list with manual download, retry, cancel and delete actions that also manage downloaded files.
- **🎦 Flexible Audio/Video Output**: Choose audio-only (AAC) or full video downloads with quality presets or selectable resolution/encoding, plus embedded metadata, chapters and artwork.
- **📚 Podcasting 2.0 Chapters Support**: Generate standard `chapters.json` chapter files so more podcast clients can display rich episode chapter navigation.
- **🍪 Restricted Content Support**: Use YouTube Data API keys and uploaded cookies to access age-restricted and members-only content more reliably.
- **🛠 Advanced yt-dlp Arguments**: Add custom yt-dlp arguments with standard syntax to fine-tune download behavior for advanced use cases.
- **⬆️ In-app yt-dlp Updates**: Upgrade the built-in yt-dlp runtime with one click to keep extraction and download compatibility current.
- **🎛 Customizable Feeds & Player**: Set per-feed custom title and cover art, and use the built-in web player for quick audio/video playback.
- **🌐 Multilingual Responsive UI**: Fully localized interface (English, Chinese, Spanish, Portuguese, Japanese, French, German, Korean) with a responsive layout across devices.

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
    volumes:
      - data:/data

volumes:
  data:
```

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

- [How to get YouTube API Key](documents/how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [How to setup YouTube Cookies](documents/youtube-cookie-setup/youtube-cookie-setup-en.md)
- [How to get YouTube channel ID](documents/how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)


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
