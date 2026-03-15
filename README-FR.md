<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>Écoutez YouTube & Bilibili. Partout.</h2>
  <h3>Si l'auto-hébergement n'est pas votre tasse de thé, jetez un œil à nos services en ligne à venir :
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

[English](README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [한국어](README-KO.md)
</div>

> [!NOTE]
> La documentation d’utilisation détaillée est maintenue en anglais dans le GitHub Wiki.
> Ce README français sert d’entrée légère au projet et peut être en retard par rapport à la documentation anglaise officielle.

## Qu’est-ce que PigeonPod ?

PigeonPod est un projet auto-hébergé pour utilisateurs techniques. Il transforme des chaînes YouTube, des playlists YouTube et du contenu Bilibili en flux RSS de podcast, avec des règles configurables de synchronisation, téléchargement et gestion.

Il convient particulièrement aux utilisateurs qui :

- veulent déployer et exploiter leur propre service
- souhaitent utiliser du contenu YouTube ou Bilibili dans une application de podcast
- ont besoin de contrôler les filtres, téléchargements automatiques, rétention et stockage

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
- **🔔 Résumés et alertes des téléchargements échoués** : Recevez des résumés par e-mail ou webhook lorsque les tentatives automatiques sont épuisées.
- **🔍 Filtres et rétention par flux** : Contrôlez la portée de la synchronisation avec mots-clés, durée et limites par flux.
- **⏱ Téléchargements plus intelligents des nouveaux épisodes** : Retardez le téléchargement automatique pour mieux traiter les vidéos fraîchement publiées.
- **🎛 Flux personnalisables et lecteur intégré** : Personnalisez titres et couvertures, puis lisez les épisodes directement sur le Web.
- **🧩 Gestion et contrôle des épisodes** : Téléchargez, relancez, annulez et supprimez les épisodes avec nettoyage des fichiers.
- **🔓 Connexion automatique en environnement de confiance** : Évitez la connexion manuelle derrière des contrôles d’accès fiables.
- **📈 Visibilité sur l’usage de l’API YouTube** : Surveillez le quota avant que les synchronisations n’atteignent la limite.
- **🔄 Export des abonnements en OPML** : Exportez vos abonnements pour migrer facilement entre clients de podcast.
- **⬆️ Gestion de yt-dlp dans l’application** : Gérez les environnements d’exécution, changez la version active et mettez à jour yt-dlp sans quitter l’application.
- **🛠 Arguments yt-dlp avancés** : Affinez les téléchargements avec des arguments yt-dlp personnalisés.
- **📚 Prise en charge des chapitres Podcasting 2.0** : Générez des fichiers de chapitres pour une navigation plus riche dans le lecteur.
- **🌐 Interface multilingue et responsive** : Utilisez PigeonPod en huit langues d’interface sur ordinateur comme sur mobile.

## Démarrage rapide

La méthode recommandée est un déploiement avec Docker Compose :

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
      # Optionnel : désactivez l’auth intégrée uniquement si une autre couche protège déjà l’interface web
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

Démarrez le service :

```bash
docker compose up -d
```

Accès :

```text
http://localhost:8834
```

Identifiants par défaut :

- Nom d’utilisateur : `root`
- Mot de passe : `Root@123`

> [!WARNING]
> `PIGEON_AUTH_ENABLED` vaut `true` par défaut. Ne le passez à `false` que si une autre couche de confiance protège déjà l’interface web, par exemple un auth proxy, un contrôle d’accès au niveau du reverse proxy, un VPN ou un réseau privé.
>
> N’exposez pas directement à Internet une instance dont l’authentification est désactivée.

## Documentation

La documentation utilisateur officielle se trouve dans le GitHub Wiki en anglais :

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)

## Liens utiles

- [README principal en anglais](README.md)
- [GitHub Wiki](https://github.com/aizhimou/PigeonPod/wiki)
- [Releases](https://github.com/aizhimou/PigeonPod/releases)
- [Issues](https://github.com/aizhimou/PigeonPod/issues)

## Notes

- La méthode de déploiement recommandée est Docker ; l’exécution directe du JAR n’est plus recommandée.
- Si vous voulez simplement voir rapidement si le projet vous convient, ce README et le Wiki anglais suffisent.
- Pour une personnalisation avancée, du développement ou du contexte d’architecture, consultez `dev-docs/` dans le dépôt.

---

<div align="center">
  <p>Créé avec ❤️ pour les passionnés de podcasts !</p>
  <p>⭐ Si vous appréciez PigeonPod, donnez-nous une étoile sur GitHub !</p>
</div>
