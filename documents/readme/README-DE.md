<div align="center">
  <img src="../../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Verwandeln Sie Ihre liebsten YouTube-Kanäle auf die einfachste und eleganteste Weise in Podcast-Kanäle.</h2>
  <h3>Falls Self-Hosting nicht Ihr Ding ist, schauen Sie sich unsere kommenden Online-Services an:
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
  [![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](README-JA.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](README-KO.md)
</div>


## Screenshots

![index-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-17-24.png)
<div align="center">
  <p style="color: gray">Kanalliste</p>
</div>

![detail-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-16-12.png)
<div align="center">
  <p style="color: gray">Kanaldetails</p>
</div>

## Kernfunktionen

- **🎯 Intelligente Abonnements & Vorschau**: Füge eine beliebige YouTube‑Kanal‑ oder Playlist‑URL ein, der Typ wird automatisch erkannt und du kannst Feed‑Details und Episodenliste vor dem Abonnieren ansehen.
- **🤖 Automatische Synchronisation & Verlauf**: Synchronisiert neue Uploads regelmäßig im Hintergrund, mit pro Feed konfigurierbarer anfänglicher Episodenanzahl und Ein-Klick‑Nachladen historischer Episoden.
- **⏱ Verzögerter Auto-Download für neue Episoden**: Konfiguriere pro Feed Verzögerungsfenster, um die Erfolgsrate von `--sponsorblock` bei neu veröffentlichten Videos zu erhöhen.
- **📻 Sicheres RSS für jeden Client**: Generiert standardkonforme RSS‑Links für Kanäle und Playlists, API‑Key‑geschützt und mit allen Podcast‑Apps kompatibel.
- **🔄 OPML-Export der Abonnements**: Exportiere alle Abonnements als standardisierte OPML-Datei für eine reibungslose Migration zwischen verschiedenen Podcast-Clients.
- **🔍 Feed‑spezifische Filter & Aufbewahrung**: Filtere Episoden nach Titel-/Beschreibungs‑Keywords (einschließen/ausschließen), Mindestdauer und lege pro Feed Sync‑Status und maximale Anzahl zu behaltender Episoden fest.
- **📊 Download‑Dashboard & Sammelaktionen**: Echtzeit‑Dashboard für Aufgaben mit Status Ausstehend/Download läuft/Abgeschlossen/Fehlgeschlagen inkl. Fehlerprotokollen und Ein-Klick‑Sammelaktionen zum Abbrechen/Löschen/Erneut versuchen.
- **🧩 Episodenverwaltung & Kontrolle**: Episodenliste mit Endlos‑Scroll, manuelle Downloads einzelner Episoden sowie erneutes Versuchen, Abbrechen und Löschen inklusive Verwaltung der lokalen Dateien.
- **🎦 Flexible Audio-/Videoausgabe**: Wähle pro Feed nur Audio (AAC) oder Video, mit Qualitätsstufen oder auswählbarer Auflösung/Encoding, und bette Metadaten, Kapitel und Cover automatisch ein.
- **📚 Podcasting 2.0 Kapitel-Unterstützung**: Erzeugt standardisierte `chapters.json`-Dateien, damit mehr Podcast-Clients die Kapitelnavigation anzeigen können.
- **🍪 Unterstützung für eingeschränkte Inhalte**: Nutzt YouTube Data API Keys und hochgeladene Cookies, um altersbeschränkte oder nur für Mitglieder verfügbare Inhalte zuverlässiger abzurufen.
- **🛠 Erweiterte yt-dlp-Argumente**: Füge benutzerdefinierte yt-dlp-Argumente in Standardsyntax hinzu, um das Downloadverhalten für fortgeschrittene Anwendungsfälle fein abzustimmen.
- **⬆️ yt-dlp-Updates in der App**: Aktualisiere die integrierte yt-dlp-Laufzeit mit einem Klick, um Extraktions- und Download-Kompatibilität aktuell zu halten.
- **🎛 Anpassbare Feeds & integrierter Player**: Individuelle Titel und Cover pro Feed sowie integrierter Web‑Player für schnelles Probehören von Audio und Video.
- **🌐 Mehrsprachige, responsive Oberfläche**: Vollständig lokalisierte Benutzeroberfläche (Englisch, Chinesisch, Spanisch, Portugiesisch, Japanisch, Französisch, Deutsch, Koreanisch) mit responsive Layout für Desktop und Mobile.

## Deployment

### Mit Docker Compose (Empfohlen)

**Stellen Sie sicher, dass Docker und Docker Compose auf Ihrem System installiert sind.**

1. Verwenden Sie die docker-compose-Konfigurationsdatei und passen Sie die Umgebungsvariablen nach Ihren Bedürfnissen an:
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
      - PIGEON_BASE_URL=https://pigeonpod.cloud # set to your domain. NOTE: If you changed this domain during use, your previous subscription links will become invalid.
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db # set to your database path
      - PIGEON_STORAGE_TYPE=LOCAL # LOCAL or S3
      - PIGEON_STORAGE_TEMP_DIR=/data/tmp/ # temporary workspace for downloads and uploads
      - PIGEON_AUDIO_FILE_PATH=/data/audio/ # local storage path (LOCAL mode)
      - PIGEON_VIDEO_FILE_PATH=/data/video/ # local storage path (LOCAL mode)
      - PIGEON_COVER_FILE_PATH=/data/cover/ # local storage path (LOCAL mode)
      - PIGEON_STORAGE_S3_ENDPOINT= # required in S3 mode, e.g. MinIO or R2 endpoint
      - PIGEON_STORAGE_S3_REGION=us-east-1 # use auto for Cloudflare R2
      - PIGEON_STORAGE_S3_BUCKET= # bucket name
      - PIGEON_STORAGE_S3_ACCESS_KEY= # S3 access key
      - PIGEON_STORAGE_S3_SECRET_KEY= # S3 secret key
      - PIGEON_STORAGE_S3_PATH_STYLE_ACCESS=true # true for MinIO and most S3-compatible services
      - PIGEON_STORAGE_S3_CONNECT_TIMEOUT_SECONDS=30
      - PIGEON_STORAGE_S3_SOCKET_TIMEOUT_SECONDS=1800
      - PIGEON_STORAGE_S3_READ_TIMEOUT_SECONDS=1800
      - PIGEON_STORAGE_S3_PRESIGN_EXPIRE_HOURS=72
    volumes:
      - data:/data

volumes:
  data:
```

2. Service starten:
```bash
docker-compose up -d
```

3. Auf die Anwendung zugreifen:
Öffnen Sie Ihren Browser und besuchen Sie `http://localhost:8834` mit **Standard-Benutzername: `root` und Standard-Passwort: `Root@123`**

### Mit JAR ausführen

**Stellen Sie sicher, dass Java 17+ und yt-dlp auf Ihrem System installiert sind.**

1. Laden Sie die neueste Release-JAR von [Releases](https://github.com/aizhimou/pigeon-pod/releases) herunter

2. Erstellen Sie ein Datenverzeichnis im gleichen Verzeichnis wie die JAR-Datei:
```bash
mkdir -p data
```

3. Anwendung ausführen:
```bash
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # auf Ihre Domain setzen. HINWEIS: Wenn Sie diese Domain während der Nutzung geändert haben, werden Ihre bisherigen Abonnement-Links ungültig.
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # auf Ihren Audio-Dateipfad setzen
           -PIGEON_VIDEO_FILE_PATH=/path/to/your/video/  \  # auf Ihren Video-Dateipfad setzen
           -PIGEON_COVER_FILE_PATH=/path/to/your/cover/  \  # auf den Pfad Ihrer Cover-Datei einstellen
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # auf Ihren Datenbankpfad setzen
           pigeon-pod-x.x.x.jar
```

4. Auf die Anwendung zugreifen:
Öffnen Sie Ihren Browser und besuchen Sie `http://localhost:8080` mit **Standard-Benutzername: `root` und Standard-Passwort: `Root@123`**

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

### MinIO and Cloudflare R2 Notes

- MinIO: use `PIGEON_STORAGE_S3_PATH_STYLE_ACCESS=true`.
- Cloudflare R2: use `PIGEON_STORAGE_S3_REGION=auto`.
- R2 web dashboard upload UI has a size limit for browser uploads, but S3 API uploads support larger files.

## Dokumentation

- [Storage guide (Local / S3 / MinIO / Cloudflare R2)](../storage-guide/storage-guide-en.md)
- [So erhalten Sie einen YouTube-API-Schlüssel](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [So richten Sie YouTube-Cookies ein](../youtube-cookie-setup/youtube-cookie-setup-en.md)
- [So erhalten Sie eine YouTube-Kanal-ID](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)

## Technologie-Stack

### Backend
- **Java 17** - Kernsprache
- **Spring Boot 3.5** - Anwendungsframework
- **MyBatis-Plus 3.5** - ORM-Framework
- **Sa-Token** - Authentifizierungsframework
- **SQLite** - Leichtgewichtige Datenbank
- **Flyway** - Datenbank-Migrationstool
- **YouTube Data API v3** - YouTube-Datenabruf
- **yt-dlp** - Video-Download-Tool
- **Rome** - RSS-Generierungsbibliothek

### Frontend
- **Javascript (ES2024)** - Kernsprache
- **React 19** - Anwendungsframework
- **Vite 7** - Build-Tool
- **Mantine 8** - UI-Komponentenbibliothek
- **i18next** - Internationalisierungsunterstützung
- **Axios** - HTTP-Client

## Entwicklungsanleitung

### Systemanforderungen
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### Lokale Entwicklung

1. Projekt klonen:
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. Datenbank konfigurieren:
```bash
# Datenverzeichnis erstellen
mkdir -p data/audio

# Datenbankdatei wird beim ersten Start automatisch erstellt
```

3. YouTube-API konfigurieren:
   - Erstellen Sie ein Projekt in der [Google Cloud Console](https://console.cloud.google.com/)
   - Aktivieren Sie die YouTube Data API v3
   - Erstellen Sie einen API-Schlüssel
   - Konfigurieren Sie den API-Schlüssel in den Benutzereinstellungen

4. Backend starten:
```bash
cd backend
mvn spring-boot:run
```

5. Frontend starten (neues Terminal):
```bash
cd frontend
npm install
npm run dev
```

6. Auf die Anwendung zugreifen:
- Frontend-Entwicklungsserver: `http://localhost:5173`
- Backend-API: `http://localhost:8080`

### Entwicklungshinweise
1. Stellen Sie sicher, dass yt-dlp installiert und über die Kommandozeile verfügbar ist
2. Konfigurieren Sie den korrekten YouTube-API-Schlüssel
3. Stellen Sie sicher, dass das Audio-Speicherverzeichnis ausreichend Festplattenspeicher hat
4. Löschen Sie regelmäßig alte Audio-Dateien, um Speicherplatz zu sparen

---

<div align="center">
  <p>Mit ❤️ für Podcast-Enthusiasten erstellt!</p>
  <p>⭐ Wenn Ihnen PigeonPod gefällt, geben Sie uns einen Stern auf GitHub!</p>
</div>
