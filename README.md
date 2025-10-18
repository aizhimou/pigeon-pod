<div align="center">
  <img src="frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Listen to YouTube. Anywhere.</h2>
  <h3>If self-hosting isn't your thing, take a look at our upcoming online services:
    <a target="_blank" href="https://pigeonpod.asimov.top/">PigeonPod</a>
  </h3>
</div>

<div align="center">

  [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](documents/readme/README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](documents/readme/README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](documents/readme/README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](documents/readme/README-JA.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](documents/readme/README-DE.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](documents/readme/README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](documents/readme/README-KO.md)
</div>

## Screenshots

![index-dark&light](documents/screenshots/index-dark&light.png)
<div align="center">
  <p style="color: gray">Channel list</p>
</div>

![detail-dark&light](documents/screenshots/detail-dark&light.png)
<div align="center">
  <p style="color: gray">Channel detail</p>
</div>

## Core Features

- **🎯 Smart Subscription**: Quickly add and sync YouTube channels or playlists with one click.
- **🤖 Auto Sync Updates**: Automatically check and sync latest channel content with incremental updates.
- **📻 RSS Podcast Subscription**: Generate standard RSS subscription links, compatible with any podcast client.
- **🔍 Content Filtering**: Support filtering by title and description keywords (include/exclude) and episode duration.
- **📊 Episode Management**: View, delete, and retry failed episode downloads.
- **🎦 Video Support**: Download video episodes with selectable resolutions and encodings; watch in podcast clients that support video.
- **🎚 Audio Quality Controls**: Choose between 0–10 quality levels or keep the original track to balance fidelity and size.
- **💽 Chapters & Metadata**: Automatically writes metadata and chapter markers to files for a better listening experience.
- **✨ Ad-free Listening**: Automatically remove intro and mid-roll ads from episodes.
- **🍪 Custom Cookies**: Supports subscription of age-restricted content and membership content by uploading cookies.
- **🌐 Multi-language Support**: Complete support for English, Chinese, Spanish, Portuguese, Japanese, French, German, Korean interfaces.
- **📱 Responsive UI**: Excellent experience on any device, anytime, anywhere.

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
      - 'PIGEON_BASE_URL=https://pigeonpod.asimov.top' # set to your domain
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # set to your audio file path
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # set to your database path
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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # set to your domain
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # set to your audio file path
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # set to your database path
           pigeon-pod-x.x.x.jar
```

4. Access the application
Open your browser and visit `http://localhost:8080` with **default username: `root` and default password: `Root@123`**


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

### Project Structure
```
pigeon-pod/
├── backend/                 # Spring Boot backend
│   ├── src/main/java/      # Java source code
│   │   └── top/asimov/pigeon/
│   │       ├── controller/ # REST API controllers
│   │       ├── service/    # Business logic services
│   │       ├── mapper/     # Data access layer
│   │       ├── model/      # Data models
│   │       ├── scheduler/  # Scheduled tasks
│   │       └── worker/     # Async workers
│   └── src/main/resources/ # Configuration files
├── frontend/               # React frontend
│   ├── src/
│   │   ├── components/     # Reusable components
│   │   ├── pages/         # Page components
│   │   ├── context/       # React Context
│   │   └── helpers/       # Utility functions
│   └── public/            # Static assets
├── data/                  # Data storage directory
│   ├── audio/            # Audio files
│   └── pigeon-pod.db     # SQLite database
├── docker-compose.yml    # Docker orchestration config
└── Dockerfile           # Docker image build
```

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
