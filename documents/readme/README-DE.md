<div align="center">
  <img src="../../documents/assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  
  <h2>Höre YouTube & Bilibili. Überall.</h2>
  <h3>Falls Self-Hosting nicht Ihr Ding ist, schauen Sie sich unsere kommenden Online-Services an:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
  [English](../../README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Français](README-FR.md) | [한국어](README-KO.md)
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

- **🎯 Intelligente Abonnements & Vorschau**: Füge eine beliebige YouTube- oder Bilibili-Kanal- bzw. Playlist-URL ein, der Typ wird automatisch erkannt und du kannst Feed-Details und Episodenliste vor dem Abonnieren ansehen.
- **📻 Sicheres RSS für jeden Client**: Generiert standardkonforme RSS‑Links für Kanäle und Playlists, API‑Key‑geschützt und mit allen Podcast‑Apps kompatibel.
- **🤖 Automatische Synchronisation & Verlauf**: Synchronisiert neue Uploads regelmäßig im Hintergrund, mit pro Feed konfigurierbarer anfänglicher Episodenanzahl und Ein-Klick‑Nachladen historischer Episoden.
- **🎦 Flexible Audio-/Videoausgabe**: Wähle pro Feed nur Audio (AAC) oder Video, mit Qualitätsstufen oder auswählbarer Auflösung/Encoding, und bette Metadaten, Kapitel und Cover automatisch ein.
- **🍪 Unterstützung für eingeschränkte Inhalte**: Nutzt YouTube Data API Keys und hochgeladene Cookies, um altersbeschränkte oder nur für Mitglieder verfügbare Inhalte zuverlässiger abzurufen.
- **📦 Stapel-Download historischer Episoden**: Speziell für effiziente Downloads älterer Episoden entwickelt, mit Suche, Pagination, Auswahl pro Folge oder Seite und Start mit einem Klick.
- **📊 Download‑Dashboard & Sammelaktionen**: Echtzeit‑Dashboard für Aufgaben mit Status Ausstehend/Download läuft/Abgeschlossen/Fehlgeschlagen inkl. Fehlerprotokollen und Ein-Klick‑Sammelaktionen zum Abbrechen/Löschen/Erneut versuchen.
- **🔍 Feed‑spezifische Filter & Aufbewahrung**: Filtere Episoden nach Titel-/Beschreibungs‑Keywords (einschließen/ausschließen), Mindestdauer und lege pro Feed Sync‑Status und maximale Anzahl zu behaltender Episoden fest.
- **⏱ Verzögerter Auto-Download für neue Episoden**: Konfiguriere pro Feed Verzögerungsfenster, um die Erfolgsrate von `--sponsorblock` bei neu veröffentlichten Videos zu erhöhen.
- **📈 Einblicke in die YouTube-API-Nutzung**: Überwache API-Quota-Verbrauch und Limits, um Synchronisierungen zu planen und unerwartete Unterbrechungen zu vermeiden.
- **🎛 Anpassbare Feeds & integrierter Player**: Individuelle Titel und Cover pro Feed sowie integrierter Web‑Player für schnelles Probehören von Audio und Video.
- **🔄 OPML-Export der Abonnements**: Exportiere alle Abonnements als standardisierte OPML-Datei für eine reibungslose Migration zwischen verschiedenen Podcast-Clients.
- **🧩 Episodenverwaltung & Kontrolle**: Episodenliste mit Endlos‑Scroll, manuelle Downloads einzelner Episoden sowie erneutes Versuchen, Abbrechen und Löschen inklusive Verwaltung der lokalen Dateien.
- **⬆️ yt-dlp-Updates in der App**: Aktualisiere die integrierte yt-dlp-Laufzeit mit einem Klick, um Extraktions- und Download-Kompatibilität aktuell zu halten.
- **🛠 Erweiterte yt-dlp-Argumente**: Füge benutzerdefinierte yt-dlp-Argumente in Standardsyntax hinzu, um das Downloadverhalten für fortgeschrittene Anwendungsfälle fein abzustimmen.
- **🌐 Mehrsprachige, responsive Oberfläche**: Vollständig lokalisierte Benutzeroberfläche (Englisch, Chinesisch, Spanisch, Portugiesisch, Japanisch, Französisch, Deutsch, Koreanisch) mit responsive Layout für Desktop und Mobile.
- **📚 Podcasting 2.0 Kapitel-Unterstützung**: Erzeugt standardisierte `chapters.json`-Dateien, damit mehr Podcast-Clients die Kapitelnavigation anzeigen können.

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
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db # set to your database path
      # Optional: integrierte Authentifizierung nur deaktivieren, wenn eine andere Schicht die Web-Oberflaeche schuetzt
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

> [!WARNING]
> `PIGEON_AUTH_ENABLED` ist standardmaessig auf `true` gesetzt. Setzen Sie den Wert nur dann auf `false`, wenn bereits eine andere vertrauenswuerdige Schutzschicht die Web-Oberflaeche absichert, zum Beispiel ein Auth-Proxy, eine Zugriffskontrolle im Reverse Proxy, ein VPN oder ein privates Netzwerk.
>
> Wenn Sie die integrierte Authentifizierung deaktivieren, muessen Sie PigeonPod auf anderem Wege absichern. Stellen Sie eine Instanz mit deaktivierter Authentifizierung nicht direkt ins oeffentliche Internet.

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
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # auf Ihren Datenbankpfad setzen
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
