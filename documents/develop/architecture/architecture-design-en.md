# PigeonPod Architecture Design

## 1. Positioning and Goals

PigeonPod is a self-hosted YouTube-to-Podcast bridge. The core goals are:

- Convert YouTube channels/playlists into subscribable RSS feeds.
- Continuously ingest metadata and download media based on policies.
- Provide local playback, download-to-local, subtitles, and chapters.
- Keep single-node deployment simple while remaining extensible.

## 2. Current Capability Map (Code as Source of Truth)

- Subscription intake: supports channel/playlist URL or ID, with automatic type detection and preview.
- Subscription config: keyword filters, duration filters, auto-download toggle, auto-download limit, auto-download delay, maximum local retained episodes, audio/video presets, subtitle settings, custom title/cover.
- Async initialization: after adding a feed, background tasks fetch episodes and dispatch download jobs.
- Incremental sync: channels sync every 1 hour, playlists every 3 hours.
- Download pipeline: `READY/PENDING/DOWNLOADING/COMPLETED/FAILED`, with auto-download, manual download, retry, cancel, and batch actions.
- Delayed auto-download: episodes are promoted to `PENDING` only after `autoDownloadAfter` is due.
- Cleanup task: enforces `maximumEpisodes` by deleting old local files and resetting episode status to `READY`.
- Media and RSS:
  - Media stream: `/media/{episodeId}.mp3|mp4|m4a`
  - Subtitle stream: `/media/{episodeId}/subtitle/{lang}.{ext}`
  - Chapters stream: `/media/{episodeId}/chapters.json`
  - RSS for both channel and playlist, protected by API key.
- Settings center: account ops, API key, YouTube API key, cookies, date format, login captcha switch, yt-dlp arg policy, feed defaults, managed yt-dlp runtime update, OPML export.
- Frontend UX: global player (audio bottom bar + video modal), dashboard status board, pagination/lazy loading, status polling, 8-language i18n.

## 3. Technology Stack and Versions

| Layer | Technologies |
| --- | --- |
| Frontend | React 19.1, Vite 7, React Router 7, Mantine 8, mantine-datatable, i18next, Axios, Plyr |
| Backend | Java 17, Spring Boot 3.5.3, Sa-Token 1.44, MyBatis-Plus 3.5.12, Flyway, Spring Retry |
| Data | SQLite (WAL mode) |
| External integrations | YouTube Data API v3, yt-dlp, Rome + iTunes modules |

## 4. Code Layout and Responsibilities

- `backend/src/main/java/top/asimov/pigeon`
  - `controller`: REST entry points (`Auth/Account/Feed/Episode/Dashboard/Media/Rss`).
  - `service`: core orchestration (feeds, episodes, accounts, RSS, yt-dlp runtime, etc.).
  - `handler`: feed-type dispatch and download execution (`ChannelFeedHandler`, `PlaylistFeedHandler`, `DownloadHandler`).
  - `helper`: YouTube ingestion and task status utilities (`Youtube*Helper`, `DownloadTaskHelper`, `TaskStatusHelper`).
  - `scheduler`: sync, download backfill, over-limit cleanup, startup recovery.
  - `event/listener`: `DownloadTaskEvent`, `EpisodesCreatedEvent`, transaction-phase listeners.
  - `mapper`: MyBatis-Plus mappers with annotation SQL.
- `frontend/src`
  - `pages`: `Home`, `Feed`, `DashboardEpisodes`, `Setting`, `Login`, `NotFound`.
  - `components`: `FeedHeader`, `EditFeedModal`, `GlobalPlayer`, `VersionUpdateAlert`, etc.
  - `context`: `UserContext` and `PlayerContext`.
  - `helpers`: Axios client, error notifications, date/duration formatting, clipboard fallback.

## 5. Backend Architecture

### 5.1 Controller Layer

- Auth: `/api/auth/login|logout|captcha|captcha-config`
- Feed: `/api/feed/**` (list/detail/fetch/preview/add/refresh/history/config/cover/delete)
- Episode: `/api/episode/**` (paging, retry, manual download, cancel, batch, download-to-local)
- Dashboard: `/api/dashboard/statistics`, `/api/dashboard/episodes`
- Account: `/api/account/**` (account ops, defaults, yt-dlp runtime, OPML, etc.)
- RSS: `/api/rss/**` (`@SaCheckApiKey` protected)
- Media: `/media/**`

### 5.2 Service and Factory Dispatch

- `FeedService` routes by `FeedType` to concrete `FeedHandler`.
- `FeedFactory` + `AbstractFeedHandler` convert `Map payload` to typed entities.
- `ChannelService` / `PlaylistService` extend `AbstractFeedService` and reuse:
  - config-update template
  - save/refresh templates
  - auto-download selection and delay strategy
  - event publishing

### 5.3 Events and Async Execution

- `DownloadTaskEvent`: async initialization tasks for channel/playlist feeds.
- `EpisodesCreatedEvent`: signals episode download execution.
- `EpisodeEventListener`: listens in `AFTER_COMMIT` phase and dispatches worker tasks.
- Thread pools in `AsyncConfig`:
  - `downloadTaskExecutor`: 3 threads, `queueCapacity=0`, `AbortPolicy`.
  - `channelSyncTaskExecutor`: 2 threads, `queueCapacity=3`.

### 5.4 Scheduled Jobs

- `ChannelSyncer`: every 1 hour.
- `PlaylistSyncer`: every 3 hours.
- `DownloadScheduler`: every 30 seconds, fills workers and promotes delayed auto-download episodes.
- `EpisodeCleaner`: every 2 hours, cleanup by feed-level limits.
- `StaleTaskCleaner`: on startup, resets stale `DOWNLOADING` rows to `PENDING`.

## 6. Data Model

- `Feed` (abstract):
  - Filtering: title/description include-exclude keywords, min/max duration
  - Download params: `downloadType`, `audioQuality`, `videoQuality`, `videoEncoding`
  - Subtitle params: `subtitleLanguages`, `subtitleFormat`
  - Auto-download: `autoDownloadEnabled`, `autoDownloadLimit`, `autoDownloadDelayMinutes`
  - Capacity: `maximumEpisodes`
  - Sync markers: `lastSyncVideoId`, `lastSyncTimestamp`
- `Channel`: adds `handler`.
- `Playlist`: adds `ownerId`.
- `Episode`:
  - primary key = YouTube video ID
  - includes `downloadStatus`, `mediaFilePath`, `mediaType`, `retryNumber`, `autoDownloadAfter`
- `PlaylistEpisode`: stores playlist mapping and `position`.
- `FeedDefaults`: system-level defaults for download and subtitle behavior.
- `User`: account fields, API key, YouTube API key, cookies, date format, yt-dlp args, login captcha toggle.

## 7. Core Flows

### 7.1 Add Subscription

1. Frontend `Home` calls `/api/feed/fetch`; backend infers `CHANNEL/PLAYLIST`.
2. `fetch` returns feed metadata + preview episodes (UI shows top 5).
3. User confirms via `/api/feed/{type}/add`.
4. `AbstractFeedService.saveFeed`:
   - applies `FeedDefaults`
   - normalizes auto-download config
   - persists feed
   - publishes `DownloadTaskEvent(INIT)`.
5. Listener executes `processChannelInitializationAsync` or `processPlaylistInitializationAsync`.

### 7.2 Sync and History Backfill

- Incremental sync through `refreshChannel` / `refreshPlaylist`:
  - channel: latest-page scan + DB diff.
  - playlist: full scan + DB diff, plus mapping/order refresh.
- History backfill via `/api/feed/{type}/history/{id}`:
  - backend fetches older pages and persists metadata.
  - current UI shows the “fetch history episodes” button only on channel detail pages.

### 7.3 Download and State Machine

1. Downloadable episodes (auto or manual) emit `EpisodesCreatedEvent`.
2. `DownloadTaskHelper.submitDownloadTask`:
   - `TaskStatusHelper.tryMarkDownloading` (`REQUIRES_NEW`) transitions `READY/PENDING/FAILED -> DOWNLOADING`
   - submits `DownloadHandler.download`.
3. `DownloadHandler`:
   - resolves feed context and effective defaults
   - builds yt-dlp command (media mode, quality, encoding, subtitles, chapters, custom args)
   - persists `mediaFilePath/mediaType/errorLog/retryNumber/downloadStatus`.
4. `DownloadScheduler` keeps worker slots filled.

### 7.4 Delayed Auto-Download

- If `autoDownloadDelayMinutes > 0` on a feed:
  - episodes remain `READY` and get `auto_download_after`.
  - scheduler promotes due rows to `PENDING` and emits download events.
- Promotion checks whether owning channel/playlist still has auto-download enabled.

### 7.5 RSS and Media Distribution

- RSS includes only `COMPLETED` episodes (local file available).
- Built with Rome + iTunes modules, with Podcasting 2.0 extensions:
  - `podcast:transcript`
  - `podcast:chapters`
- Browser local-download endpoint: `/api/episode/download/local/{id}`.

## 8. Frontend Architecture and Interaction

- Routes:
  - `/`: feed list + add-subscription + dashboard statistic cards
  - `/:type/:feedId`: feed detail (pagination, search, sort, polling, actions)
  - `/dashboard/episodes/:status`: status board + batch operations
  - `/user-setting`: system settings
- State management:
  - `UserContext` for auth/session and user preferences
  - `PlayerContext` for global player state
- Global player:
  - audio plays in fixed bottom bar
  - video auto-opens in centered modal
- Polling:
  - Home statistics every 3 seconds
  - Feed detail active tasks every 3 seconds
  - Dashboard episode list every 3 seconds

## 9. Internationalization and Error Handling

- Both frontend and backend support `en/zh/es/ja/pt/fr/de/ko`.
- Frontend Axios injects `Accept-Language` automatically.
- Backend uses `HeaderLocaleResolver` + `MessageSource` for localized responses.
- Unified exception pipeline: `BusinessException` + `GlobalExceptionHandler`.

## 10. Configuration and Deployment

- Core config lives in `backend/src/main/resources/application.yml`:
  - SQLite WAL, upload limits, Sa-Token, media directories, yt-dlp managed root.
- `YtDlpRuntimeService` supports:
  - managed runtime (`python3 -m yt_dlp`)
  - `stable/nightly` channel update
  - version retention and rollback
  - fallback to system `yt-dlp` binary when managed runtime is unavailable.
- Frontend dev proxies `/api` and `/media` to `localhost:8080` via `vite.config.js`.

## 11. Extension Guidelines

- New feed type: implement `FeedHandler + AbstractFeedService` subclass and register with `FeedFactory`.
- New download parameters: extend `Feed/FeedDefaults`, then wire `EditFeedModal -> API -> DownloadHandler`.
- New scheduled behavior: follow existing `@Scheduled + Service` pattern and reuse status transitions.
- Documentation discipline: update this file and the Chinese architecture file whenever model/flow/API changes are introduced.
