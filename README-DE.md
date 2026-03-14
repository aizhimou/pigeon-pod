<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>Höre YouTube und Bilibili. Überall.</h2>
  <h3>Wenn Self-Hosting nicht dein Ding ist, schau dir unsere kommenden Online-Dienste an:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

[English](README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

> [!NOTE]
> Die ausführliche Nutzerdokumentation wird derzeit auf Englisch im GitHub Wiki gepflegt.
> Dieses deutsche README ist nur ein schlanker Projekteinstieg und kann hinter der aktuellen englischen Dokumentation zurückliegen.

## Was ist PigeonPod?

PigeonPod ist ein Self-Hosting-Projekt für technische Nutzer. Es wandelt YouTube-Kanäle, YouTube-Playlists und Bilibili-Inhalte in abonnierbare Podcast-RSS-Feeds um und gibt dir Kontrolle über Synchronisierung, Downloads und Verwaltungsregeln.

Es eignet sich besonders für Nutzer, die:

- ihren eigenen Dienst selbst betreiben möchten
- Inhalte aus YouTube oder Bilibili in Podcast-Apps nutzen wollen
- Filter, Auto-Downloads, Aufbewahrung und Speicherstrategie kontrollieren möchten

## Hauptfunktionen

- **🎯 Intelligente Abos und Vorschau**: Abonniere YouTube- oder Bilibili-Kanäle und Playlists in Sekunden.
- **📻 Sichere RSS-Feeds für jeden Client**: Erzeuge geschützte Standard-RSS-Feeds für jede Podcast-App.
- **🎦 Flexible Audio-/Video-Ausgabe**: Lade als Audio oder Video herunter und steuere Qualität und Format.
- **🤖 Automatische Synchronisierung und Verlauf**: Halte Abonnements aktuell und hole bei Bedarf ältere Videos nach.
- **🍪 Erweiterte Cookie-Unterstützung**: Nutze YouTube- und Bilibili-Cookies für zuverlässigeren Zugriff auf eingeschränkte Inhalte.
- **🌍 Proxy-fähiger Netzwerkzugriff**: Leite YouTube-API- und yt-dlp-Verkehr über benutzerdefinierte Proxys.
- **🔗 Ein-Klick-Episodenfreigabe**: Teile jede Episode über eine öffentliche Seite zur direkten Wiedergabe ohne Login.
- **📦 Schnelle Batch-Downloads**: Durchsuche, wähle und queue große historische Kataloge effizient.
- **📊 Download-Dashboard und Massenaktionen**: Verfolge Aufgabenstatus und führe Retry, Cancel oder Delete gesammelt aus.
- **🔍 Feed-spezifische Filter und Aufbewahrung**: Steuere den Sync-Umfang mit Schlüsselwörtern, Dauer und Episodenlimits.
- **⏱ Intelligentere Downloads neuer Episoden**: Verzögere Auto-Downloads, um die Verarbeitung frisch veröffentlichter Videos zu verbessern.
- **🎛 Anpassbare Feeds und integrierter Player**: Passe Titel und Cover an und spiele Episoden direkt im Web ab.
- **🧩 Episodenverwaltung und Kontrolle**: Lade herunter, versuche erneut, brich ab oder lösche Episoden inklusive Dateibereinigung.
- **🔓 Automatischer Login in vertrauenswürdigen Umgebungen**: Überspringe die manuelle Anmeldung hinter vertrauenswürdigen Zugriffskontrollen.
- **📈 Einblicke in die YouTube-API-Nutzung**: Überwache die Quote, bevor Synchronisierungen an Limits stoßen.
- **🔄 OPML-Export der Abonnements**: Exportiere Abonnements für einen einfachen Wechsel zwischen Podcast-Clients.
- **⬆️ yt-dlp-Updates in der App**: Aktualisiere yt-dlp, ohne die App zu verlassen.
- **🛠 Erweiterte yt-dlp-Argumente**: Feineinstellung von Downloads mit benutzerdefinierten yt-dlp-Argumenten.
- **📚 Podcasting 2.0 Kapitel-Unterstützung**: Erzeuge Kapiteldateien für eine reichhaltigere Navigation im Player.
- **🌐 Mehrsprachige, responsive Oberfläche**: Nutze PigeonPod in acht UI-Sprachen auf Desktop und Mobilgeräten.

## Schnellstart

Empfohlen wird die Bereitstellung mit Docker Compose:

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
      # Optional: integrierte Authentifizierung nur deaktivieren, wenn eine andere Schicht die Web-Oberfläche schützt
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

Starte den Dienst:

```bash
docker compose up -d
```

Aufruf:

```text
http://localhost:8834
```

Standardzugang:

- Benutzername: `root`
- Passwort: `Root@123`

> [!WARNING]
> `PIGEON_AUTH_ENABLED` ist standardmäßig auf `true` gesetzt. Setze den Wert nur dann auf `false`, wenn bereits eine andere vertrauenswürdige Schutzschicht die Web-Oberfläche absichert, zum Beispiel ein Auth-Proxy, eine Zugriffskontrolle im Reverse Proxy, ein VPN oder ein privates Netzwerk.
>
> Stelle eine Instanz mit deaktivierter Authentifizierung nicht direkt ins öffentliche Internet.

## Dokumentation

Die offizielle Nutzerdokumentation liegt im englischen GitHub Wiki:

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)

## Nützliche Links

- [Englisches Haupt-README](README.md)
- [GitHub Wiki](https://github.com/aizhimou/PigeonPod/wiki)
- [Releases](https://github.com/aizhimou/PigeonPod/releases)
- [Issues](https://github.com/aizhimou/PigeonPod/issues)

## Hinweise

- Die empfohlene Betriebsform ist Docker; das direkte Ausführen des JAR wird nicht mehr empfohlen.
- Wenn du nur schnell einschätzen willst, ob das Projekt zu dir passt, reichen dieses README und das englische Wiki.
- Für tiefere Anpassungen, Entwicklung oder Architekturkontext nutze `dev-docs/` im Repository.

---

<div align="center">
  <p>Mit ❤️ für Podcast-Enthusiasten erstellt!</p>
  <p>⭐ Wenn dir PigeonPod gefällt, gib uns einen Stern auf GitHub!</p>
</div>
