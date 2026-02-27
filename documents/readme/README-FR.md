<div align="center">
  <img src="../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Écoutez YouTube & Bilibili. Partout.</h2>
  <h3>Si l'auto-hébergement n'est pas votre tasse de thé, jetez un œil à nos services en ligne à venir :
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[English](../../README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [한국어](README-KO.md)
</div>

## Captures d'écran

![index-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-17-24.png)
<div align="center">
  <p style="color: gray">Liste des chaînes</p>
</div>

![detail-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-16-12.png)
<div align="center">
  <p style="color: gray">Détails de la chaîne</p>
</div>

## Fonctionnalités principales

- **🎯 Abonnement intelligent avec prévisualisation** : Collez n'importe quelle URL de chaîne ou de playlist YouTube ou Bilibili, le type est détecté automatiquement et vous pouvez prévisualiser le flux et les épisodes avant de vous abonner.
- **📻 Flux RSS sécurisés pour tous les clients** : Génère des liens RSS standard pour les chaînes et playlists, protégés par clé API et compatibles avec toutes les applications de podcast.
- **🤖 Synchronisation automatique et rattrapage de l'historique** : Synchronise périodiquement les nouvelles mises en ligne en arrière‑plan, avec un nombre initial d'épisodes configurable par flux et un chargement des épisodes historiques en un clic.
- **🎦 Sortie audio/vidéo flexible** : Choisissez des téléchargements audio seuls (AAC) ou vidéo, avec préréglages de qualité ou sélection de résolution/encodage, et insertion automatique des métadonnées, chapitres et illustrations.
- **🍪 Prise en charge des contenus restreints** : Utilise des clés YouTube Data API et des cookies téléversés pour accéder plus fiablement aux contenus avec restriction d'âge ou réservés aux membres.
- **📦 Téléchargement en lot des épisodes historiques** : Conçu spécifiquement pour télécharger efficacement l’historique, avec recherche, pagination, sélection par épisode ou par page et lancement en un clic.
- **📊 Tableau de bord des téléchargements et actions groupées** : Tableau de bord en temps réel pour les tâches en attente/en cours/terminées/échouées, avec journaux d'erreurs et actions groupées pour annuler/supprimer/réessayer en un clic.
- **🔍 Filtres et rétention par flux** : Filtrez les épisodes par mots‑clés dans le titre/la description (inclure/exclure), durée minimale, et définissez pour chaque flux l'état de synchronisation et le nombre maximal d'épisodes conservés.
- **⏱ Téléchargement automatique différé des nouveaux épisodes** : Configurez des fenêtres de délai par flux pour améliorer le taux de réussite de `--sponsorblock` sur les vidéos nouvellement publiées.
- **📈 Visibilité sur l’usage de l’API YouTube** : Surveillez la consommation de quota et les limites API pour planifier les synchronisations et éviter les interruptions inattendues.
- **🎛 Flux personnalisables et lecteur intégré** : Personnalisez le titre et la couverture de chaque flux et utilisez le lecteur Web intégré pour écouter rapidement l'audio ou la vidéo.
- **🔄 Export des abonnements en OPML** : Exportez tous les abonnements sous forme de fichier OPML standard pour migrer facilement entre différents clients de podcast.
- **🧩 Gestion et contrôle des épisodes** : Liste d'épisodes avec défilement infini, téléchargement manuel, réessai, annulation et suppression d'épisodes individuels, tout en gérant également les fichiers locaux correspondants.
- **⬆️ Mise à jour yt-dlp intégrée** : Mettez à niveau en un clic le runtime yt-dlp intégré pour conserver une compatibilité d'extraction et de téléchargement à jour.
- **🛠 Arguments yt-dlp avancés** : Ajoutez des arguments yt-dlp personnalisés avec la syntaxe standard pour affiner le comportement de téléchargement dans les cas avancés.
- **🌐 Interface multilingue et responsive** : Interface entièrement localisée (anglais, chinois, espagnol, portugais, japonais, français, allemand, coréen) avec mise en page responsive pour bureau et mobile.
- **📚 Prise en charge des chapitres Podcasting 2.0** : Génère des fichiers de chapitres `chapters.json` standard afin que davantage de clients de podcast puissent afficher la navigation par chapitres.

## Déploiement

### Utilisation de Docker Compose (Recommandé)

**Assurez-vous d'avoir Docker et Docker Compose installés sur votre machine.**

1. Utilisez le fichier de configuration docker-compose, modifiez les variables d'environnement selon vos besoins :
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

2. Démarrez le service :
```bash
docker-compose up -d
```

3. Accédez à l'application :
Ouvrez votre navigateur et visitez `http://localhost:8834` avec **nom d'utilisateur par défaut : `root` et mot de passe par défaut : `Root@123`**

### Exécution avec JAR

**Assurez-vous d'avoir Java 17+ et yt-dlp installés sur votre machine.**

1. Téléchargez le JAR de la dernière version depuis [Releases](https://github.com/aizhimou/pigeon-pod/releases)

2. Créez le répertoire de données dans le même répertoire que le fichier JAR :
```bash
mkdir -p data
```

3. Exécutez l'application :
```bash
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # définissez le chemin de votre base de données
           pigeon-pod-x.x.x.jar
```

4. Accédez à l'application :
Ouvrez votre navigateur et visitez `http://localhost:8080` avec **nom d'utilisateur par défaut : `root` et mot de passe par défaut : `Root@123`**

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

- [Comment obtenir une clé API YouTube](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [Comment configurer les cookies YouTube](../youtube-cookie-setup/youtube-cookie-setup-en.md)
- [Comment obtenir l'ID de chaîne YouTube](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)

## Stack technologique

### Backend
- **Java 17** - Langage principal
- **Spring Boot 3.5** - Framework d'application
- **MyBatis-Plus 3.5** - Framework ORM
- **Sa-Token** - Framework d'authentification
- **SQLite** - Base de données légère
- **Flyway** - Outil de migration de base de données
- **YouTube Data API v3** - Récupération de données YouTube
- **yt-dlp** - Outil de téléchargement vidéo
- **Rome** - Bibliothèque de génération RSS

### Frontend
- **Javascript (ES2024)** - Langage principal
- **React 19** - Framework d'application
- **Vite 7** - Outil de build
- **Mantine 8** - Bibliothèque de composants UI
- **i18next** - Support d'internationalisation
- **Axios** - Client HTTP

## Guide de développement

### Prérequis d'environnement
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### Développement local

1. Clonez le projet :
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. Configurez la base de données :
```bash
# Créez le répertoire de données
mkdir -p data/audio

# Le fichier de base de données sera créé automatiquement au premier démarrage
```

3. Configurez l'API YouTube :
   - Créez un projet dans la [Console Google Cloud](https://console.cloud.google.com/)
   - Activez l'API YouTube Data v3
   - Créez une clé API
   - Configurez la clé API dans les paramètres utilisateur

4. Démarrez le backend :
```bash
cd backend
mvn spring-boot:run
```

5. Démarrez le frontend (nouveau terminal) :
```bash
cd frontend
npm install
npm run dev
```

6. Accédez à l'application :
- Serveur de développement frontend : `http://localhost:5173`
- API backend : `http://localhost:8080`

### Notes de développement
1. Assurez-vous que yt-dlp soit installé et disponible en ligne de commande
2. Configurez correctement la clé API YouTube
3. Assurez-vous que le répertoire de stockage audio dispose d'un espace disque suffisant
4. Nettoyez régulièrement les anciens fichiers audio pour économiser l'espace

---

<div align="center">
  <p>Créé avec ❤️ pour les passionnés de podcasts !</p>
  <p>⭐ Si vous appréciez PigeonPod, donnez-nous une étoile sur GitHub !</p>
</div>
