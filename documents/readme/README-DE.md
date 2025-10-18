<div align="center">
  <img src="../../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Verwandeln Sie Ihre liebsten YouTube-Kanäle auf die einfachste und eleganteste Weise in Podcast-Kanäle.</h2>
  <h3>Falls Self-Hosting nicht Ihr Ding ist, schauen Sie sich unsere kommenden Online-Services an:
    <a target="_blank" href="https://pigeonpod.asimov.top/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
  [![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](README-JA.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](README-KO.md)
</div>


## Screenshots

![index-dark&light](../screenshots/index-dark&light.png)
<div align="center">
  <p style="color: gray">Kanalliste</p>
</div>

![detail-dark&light](../screenshots/detail-dark&light.png)
<div align="center">
  <p style="color: gray">Kanaldetails</p>
</div>

## Kernfunktionen

- **🎯 Intelligente Abonnements**: YouTube-Kanäle oder Playlists mit einem Klick hinzufügen und synchron halten.
- **🤖 Automatische Synchronisation**: Automatische Überprüfung und Synchronisation der neuesten Kanalinhalte mit inkrementellen Updates.
- **📻 RSS-Podcast-Abonnements**: Generierung standardmäßiger RSS-Abonnement-Links, kompatibel mit jedem Podcast-Client.
- **🔍 Inhaltsfilterung**: Unterstützung für Titel- und Beschreibungs-Stichwörter (einschließen/ausschließen) sowie Episodenlänge.
- **📊 Episodenverwaltung**: Episoden anzeigen, löschen und fehlgeschlagene Episode-Downloads wiederholen.
- **🎦 Videounterstützung**: Unterstützt das Herunterladen von Videoepisoden mit wählbaren Auflösungen und Codecs; abspielbar in Podcast-Clients mit Videounterstützung.
- **🎚 Audioqualität konfigurieren**: Zwischen Qualitätsstufen 0–10 wählen oder die Originalspur behalten, um Qualität und Dateigröße auszubalancieren.
- **💽 Kapitel und Metadaten**: Schreibt automatisch Metadaten und Kapitelmarken in die Dateien und verbessert das Hörerlebnis.
- **✨ Werbefreies Hören**: Automatische Entfernung von Intro- und Mid-Roll-Werbung aus Episoden.
- **🍪 Benutzerdefinierte Cookies**: Unterstützt Abonnements von altersbeschränkten Inhalten und Mitgliedschaftsinhalten durch Cookie-Upload.
- **🌐 Mehrsprachige Unterstützung**: Vollständige Unterstützung für englische, chinesische, spanische, portugiesische, japanische, französische, deutsche und koreanische Benutzeroberflächen.
- **📱 Responsive Benutzeroberfläche**: Hervorragende Erfahrung auf jedem Gerät, jederzeit und überall.

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
      - 'PIGEON_BASE_URL=https://pigeonpod.asimov.top' # auf Ihre Domain setzen
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # auf Ihren Audio-Dateipfad setzen
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # auf Ihren Datenbankpfad setzen
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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # auf Ihre Domain setzen
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # auf Ihren Audio-Dateipfad setzen
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # auf Ihren Datenbankpfad setzen
           pigeon-pod-x.x.x.jar
```

4. Auf die Anwendung zugreifen:
Öffnen Sie Ihren Browser und besuchen Sie `http://localhost:8080` mit **Standard-Benutzername: `root` und Standard-Passwort: `Root@123`**

## Dokumentation

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

### Projektstruktur
```
pigeon-pod/
├── backend/                 # Spring Boot Backend
│   ├── src/main/java/      # Java-Quellcode
│   │   └── top/asimov/pigeon/
│   │       ├── controller/ # REST-API-Controller
│   │       ├── service/    # Geschäftslogik-Services
│   │       ├── mapper/     # Datenzugriffsschicht
│   │       ├── model/      # Datenmodelle
│   │       ├── scheduler/  # Geplante Aufgaben
│   │       └── worker/     # Asynchrone Worker
│   └── src/main/resources/ # Konfigurationsdateien
├── frontend/               # React Frontend
│   ├── src/
│   │   ├── components/     # Wiederverwendbare Komponenten
│   │   ├── pages/         # Seitenkomponenten
│   │   ├── context/       # React Context
│   │   └── helpers/       # Hilfsfunktionen
│   └── public/            # Statische Assets
├── data/                  # Datenspeicherverzeichnis
│   ├── audio/            # Audio-Dateien
│   └── pigeon-pod.db     # SQLite-Datenbank
├── docker-compose.yml    # Docker-Orchestrierungskonfiguration
└── Dockerfile           # Docker-Image-Build
```

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
