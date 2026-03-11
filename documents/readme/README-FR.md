<div align="center">
  <img src="../documents/assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  
  <h2>Écoutez YouTube & Bilibili. Partout.</h2>
  <h3>Si l'auto-hébergement n'est pas votre tasse de thé, jetez un œil à nos services en ligne à venir :
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[English](../../README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [한국어](README-KO.md)
</div>

## Captures d'écran

![index-dark&light](documents/assets/screenshots/home-27-11-2025.png)
<div align="center">
  <p style="color: gray">Liste des chaînes</p>
</div>

![detail-dark&light](documents/assets/screenshots/feed-27-11-2025.png)
<div align="center">
  <p style="color: gray">Détails de la chaîne</p>
</div>

## Fonctionnalités principales

- **🎯 Abonnement intelligent avec prévisualisation** : Abonnez-vous à des chaînes et playlists YouTube ou Bilibili en quelques secondes.
- **📻 Flux RSS sécurisés pour tous les clients** : Générez des flux RSS standard protégés pour n'importe quelle application de podcast.
- **🎦 Sortie audio/vidéo flexible** : Téléchargez en audio ou en vidéo avec contrôle de la qualité et du format.
- **🤖 Synchronisation automatique et historique** : Gardez vos abonnements à jour et récupérez les anciennes vidéos à la demande.
- **🍪 Prise en charge étendue des cookies** : Utilisez les cookies YouTube et Bilibili pour accéder plus fiablement aux contenus restreints.
- **🌍 Accès réseau via proxy** : Faites passer le trafic de l’API YouTube et de yt-dlp par des proxys personnalisés.
- **🔗 Partage d’épisodes en un clic** : Partagez n’importe quel épisode avec une page publique lisible sans connexion.
- **📦 Téléchargements par lots rapides** : Recherchez, sélectionnez et mettez en file de grands catalogues historiques efficacement.
- **📊 Tableau de bord des téléchargements et actions groupées** : Suivez l’état des tâches et relancez, annulez ou supprimez en lot.
- **🔍 Filtres et rétention par flux** : Contrôlez la portée de la synchronisation avec mots-clés, durée et limites par flux.
- **⏱ Téléchargements plus intelligents des nouveaux épisodes** : Retardez le téléchargement automatique pour mieux traiter les vidéos fraîchement publiées.
- **🎛 Flux personnalisables et lecteur intégré** : Personnalisez titres et couvertures, puis lisez les épisodes directement sur le Web.
- **🧩 Gestion et contrôle des épisodes** : Téléchargez, relancez, annulez et supprimez les épisodes avec nettoyage des fichiers.
- **🔓 Connexion automatique en environnement de confiance** : Évitez la connexion manuelle derrière des contrôles d’accès fiables.
- **📈 Visibilité sur l’usage de l’API YouTube** : Surveillez le quota avant que les synchronisations n’atteignent la limite.
- **🔄 Export des abonnements en OPML** : Exportez vos abonnements pour migrer facilement entre clients de podcast.
- **⬆️ Mise à jour yt-dlp intégrée** : Mettez à jour yt-dlp sans quitter l’application.
- **🛠 Arguments yt-dlp avancés** : Affinez les téléchargements avec des arguments yt-dlp personnalisés.
- **📚 Prise en charge des chapitres Podcasting 2.0** : Générez des fichiers de chapitres pour une navigation plus riche dans le lecteur.
- **🌐 Interface multilingue et responsive** : Utilisez PigeonPod en huit langues d’interface sur ordinateur comme sur mobile.

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
      # Optionnel : desactivez l'auth integree uniquement si une autre couche protege deja l'interface web
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

> [!WARNING]
> `PIGEON_AUTH_ENABLED` vaut `true` par defaut. Ne le passez a `false` que si une autre couche de confiance protege deja l'interface web, par exemple un auth proxy, un controle d'acces au niveau du reverse proxy, un VPN ou un reseau prive.
>
> Si vous desactivez l'authentification integree, vous devez proteger PigeonPod par un autre moyen. N'exposez pas directement a Internet une instance dont l'authentification est desactivee.

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
