<div align="center">
  <img src="../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Transformez vos chaînes YouTube préférées en chaînes de podcast de la manière la plus simple et élégante.</h2>
  <h3>Si l'auto-hébergement n'est pas votre tasse de thé, jetez un œil à nos services en ligne à venir :
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](README-ES.md) [![Português README](https://img.shields.io/badge/README-Português-green)](README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](README-JA.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](README-DE.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](README-KO.md)
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

- **🎯 Abonnement intelligent avec prévisualisation** : Collez n'importe quelle URL de chaîne ou de playlist YouTube, le type est détecté automatiquement et vous pouvez prévisualiser le flux et les épisodes avant de vous abonner.
- **🤖 Synchronisation automatique et rattrapage de l'historique** : Synchronise périodiquement les nouvelles mises en ligne en arrière‑plan, avec un nombre initial d'épisodes configurable par flux et un chargement des épisodes historiques en un clic.
- **⏱ Téléchargement automatique différé des nouveaux épisodes** : Configurez des fenêtres de délai par flux pour améliorer le taux de réussite de `--sponsorblock` sur les vidéos nouvellement publiées.
- **📻 Flux RSS sécurisés pour tous les clients** : Génère des liens RSS standard pour les chaînes et playlists, protégés par clé API et compatibles avec toutes les applications de podcast.
- **🔄 Export des abonnements en OPML** : Exportez tous les abonnements sous forme de fichier OPML standard pour migrer facilement entre différents clients de podcast.
- **🔍 Filtres et rétention par flux** : Filtrez les épisodes par mots‑clés dans le titre/la description (inclure/exclure), durée minimale, et définissez pour chaque flux l'état de synchronisation et le nombre maximal d'épisodes conservés.
- **📊 Tableau de bord des téléchargements et actions groupées** : Tableau de bord en temps réel pour les tâches en attente/en cours/terminées/échouées, avec journaux d'erreurs et actions groupées pour annuler/supprimer/réessayer en un clic.
- **🧩 Gestion et contrôle des épisodes** : Liste d'épisodes avec défilement infini, téléchargement manuel, réessai, annulation et suppression d'épisodes individuels, tout en gérant également les fichiers locaux correspondants.
- **🎦 Sortie audio/vidéo flexible** : Choisissez des téléchargements audio seuls (AAC) ou vidéo, avec préréglages de qualité ou sélection de résolution/encodage, et insertion automatique des métadonnées, chapitres et illustrations.
- **📚 Prise en charge des chapitres Podcasting 2.0** : Génère des fichiers de chapitres `chapters.json` standard afin que davantage de clients de podcast puissent afficher la navigation par chapitres.
- **🍪 Prise en charge des contenus restreints** : Utilise des clés YouTube Data API et des cookies téléversés pour accéder plus fiablement aux contenus avec restriction d'âge ou réservés aux membres.
- **🛠 Arguments yt-dlp avancés** : Ajoutez des arguments yt-dlp personnalisés avec la syntaxe standard pour affiner le comportement de téléchargement dans les cas avancés.
- **⬆️ Mise à jour yt-dlp intégrée** : Mettez à niveau en un clic le runtime yt-dlp intégré pour conserver une compatibilité d'extraction et de téléchargement à jour.
- **🎛 Flux personnalisables et lecteur intégré** : Personnalisez le titre et la couverture de chaque flux et utilisez le lecteur Web intégré pour écouter rapidement l'audio ou la vidéo.
- **🌐 Interface multilingue et responsive** : Interface entièrement localisée (anglais, chinois, espagnol, portugais, japonais, français, allemand, coréen) avec mise en page responsive pour bureau et mobile.

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
      - 'PIGEON_BASE_URL=https://pigeonpod.cloud' # définissez votre domaine. REMARQUE : Si vous avez modifié ce domaine en cours d'utilisation, vos précédents liens d'abonnement deviendront invalides.
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # définissez le chemin de vos fichiers audio
      - 'PIGEON_VIDEO_FILE_PATH=/data/video/' # définissez le chemin de vos fichiers vidéo
      - 'PIGEON_COVER_FILE_PATH=/data/cover/' # définir le chemin de votre fichier de couverture
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # définissez le chemin de votre base de données
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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # définissez votre domaine. REMARQUE : Si vous avez modifié ce domaine en cours d'utilisation, vos précédents liens d'abonnement deviendront invalides.
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # définissez le chemin de vos fichiers audio
           -PIGEON_VIDEO_FILE_PATH=/path/to/your/video/  \  # définissez le chemin de vos fichiers vidéo
           -PIGEON_COVER_FILE_PATH=/path/to/your/cover/  \  # définissez le chemin de votre fichier de couverture
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # définissez le chemin de votre base de données
           pigeon-pod-x.x.x.jar
```

4. Accédez à l'application :
Ouvrez votre navigateur et visitez `http://localhost:8080` avec **nom d'utilisateur par défaut : `root` et mot de passe par défaut : `Root@123`**

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
