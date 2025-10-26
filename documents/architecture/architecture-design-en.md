# PigeonPod Architecture

## 1. Background & Goals

- **Purpose**: Self-hosted tool that turns YouTube channels or playlists into podcast-ready RSS feeds so any podcast client can subscribe, stream, or download them.
- **Goals**: Keep personal deployments simple, automate ingestion/downloading, deliver a multilingual UX, and maintain a clear structure that scales for solo development or AI-assisted collaboration.

## 2. Capabilities

- One-click subscription for channels or playlists with preview and filter configuration.
- Incremental synchronization with optional historical backfill and asynchronous bulk downloading.
- Audio/video downloads with quality and codec options, plus custom cover/title support.
- RSS generation with iTunes extensions, protected by API keys for external podcast clients.
- Account management: login, password reset, username change, API key generation, YouTube API key storage, cookies upload.
- End-to-end localization (backend messages and frontend UI).
- Operational helpers: download scheduling, retry flows, version update alerts.

## 3. Technology Stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5 (Web, Scheduling, Async, Retry, Cache), MyBatis-Plus, SQLite, Flyway, Sa-Token, Rome RSS, YouTube Data API v3, yt-dlp |
| Frontend | React 19, Vite 7, Mantine 8, React Router, Axios, i18next, Mantine Notifications |
| Tooling / Infra | Maven, Node.js, Docker Compose, local filesystem for audio & covers |

## 4. Architecture Overview

1. **Controller layer**: Exposes `/api/**` and `/media/**`, handles request validation, Sa-Token annotations, and response envelopes.
2. **Service layer**: `FeedService` registers handlers and routes workflow; `ChannelService`/`PlaylistService` extend `AbstractFeedService` to manage the feed lifecycle; supporting services (Episodes, Media, Account, etc.) encapsulate domain logic.
3. **Events & Schedulers**: Spring events (`EpisodesCreatedEvent`, `DownloadTaskEvent`) plus `ChannelSyncer`, `PlaylistSyncer`, `DownloadScheduler` form the “discover → download → deliver” pipeline.
4. **Persistence**: MyBatis-Plus mappers backed by SQLite; Flyway handles schema migrations.
5. **Frontend SPA**: React app powered by Mantine, Axios, and i18next; UserContext stores auth state and localStorage ensures persistence.

## 5. Data Model

- **Feed (abstract)**: ID, title/custom title, cover/custom cover, source type, keyword & duration filters, initial/max episode count, download type & quality, last-sync markers.
- **Channel / Playlist**: Extend Feed with handler, ownerId, episode sort options, etc.
- **Episode**: Episode ID, channel ID, title/description, published date, cover URLs, ISO 8601 duration, download status, media path, MIME, error log, retry count, creation timestamp.
- **User**: Username, hashed password & salt, API key, YouTube API key, cookies, timestamps.

## 6. Core Flows

1. **Subscription onboarding**  
   - Input URL → `FeedService.fetch` infers the type, calls YouTube API, and returns feed + preview episodes.  
   - On confirmation `FeedService.add` calls `AbstractFeedService.saveFeed`, which either runs synchronously (immediate fetch + download events) or asynchronously (publish `DownloadTaskEvent` for background processing) depending on initial episode count.

2. **Incremental sync**  
   - `ChannelSyncer` / `PlaylistSyncer` scan feeds whose `lastSyncTimestamp` is stale, call `refreshFeed`, persist new episodes, update markers, and emit download events.

3. **Download pipeline**  
   - `EpisodesCreatedEvent` → `EpisodeEventListener` → `DownloadTaskSubmitter` (REQUIRES_NEW transaction to flip status) → thread pool executes `DownloadWorker`.  
   - `DownloadWorker` assembles yt-dlp commands (audio/video settings, cookies file), stores media path/MIME/status on success, and logs failures with retry counters.  
   - `DownloadScheduler` runs every 30s, fills available thread slots with PENDING episodes, then FAILED ones with retries left.

4. **RSS & media delivery**  
   - `/api/rss/...` (API key protected) reads feed/episodes and uses Rome + iTunes modules to emit RSS with enclosure URLs pointing to `/media/{episodeId}.{ext}`.  
   - `/media/...` ensures the file resides under approved directories and streams it with proper MIME/headers.

## 7. Frontend–Backend Collaboration

- Axios singleton configures `baseURL` and `Accept-Language`; 401 responses trigger logout and redirect to `/login`.
- React Router defines routes; UserContext + localStorage keeps auth state after refreshes.
- Mantine components and hooks (`useDisclosure`, `useMediaQuery`, etc.) drive UX, while shared components (Header, EditFeedModal, CopyModal, VersionUpdateAlert) centralize repeated logic.
- Notification helpers (`showSuccess`, `showError`) provide consistent feedback for all operations.

## 8. Internationalization

- **Backend**: Spring `MessageSource` with language-specific property files; custom `LocaleResolver` inspects request headers; `BusinessException` references message keys so localized responses flow through Sa-Token’s result format.
- **Frontend**: i18next loads eight language packs, stores selection in localStorage, and exposes translations via `useTranslation`; Axios propagates the chosen language to the backend.

## 9. Error Handling & Reliability

- Backend exceptions funnel through a global handler that distinguishes business vs. system failures; download/file operations log details and guard against partial writes.
- Download submission uses dedicated transactions and Spring Retry to reduce race conditions and mark failures cleanly.
- Frontend `showError` categorizes messages by HTTP status (401/429/500) before falling back to generic alerts.
- Scheduled jobs and async listeners wrap their work in try/catch so individual feed failures never block the overall pipeline.

## 10. Security

- Sa-Token manages user sessions and annotation-based guards; RSS endpoints require API keys.  
- Media access is limited to configured directories; cover uploads validate MIME/size; cookies are stored per user and written to temporary files that are deleted post-download.  
- Deployment config (`pigeon.base-url`, file paths) must reference safe directories to avoid leaking sensitive data.

## 11. Configuration & Deployment

- `application.yml` defines defaults (port, SQLite URL, Flyway, upload limits, logging); override via environment variables in production.  
- Ensure yt-dlp is installed and the audio/cover folders are writable.  
- Docker Compose is recommended: map port 8080, mount a persistent data volume, set BASE_URL/audio/cover paths, and override JDBC URLs.  
- Use debug logging in development and info in production; integrate external log aggregation if needed.

## 12. Extension & Collaboration Tips

- Adding feed types: implement a new `FeedHandler` + `AbstractFeedService` subclass and register it with `FeedFactory` to reuse the same lifecycle.  
- Download strategy: extend feed configuration with richer audio/video knobs; `DownloadWorker` reads everything from a single context object.  
- Monitoring: optionally add Spring Actuator, a download dashboard, or a health endpoint.  
- Documentation: keep this architecture note up to date and introduce ADRs under `documents/` for major decisions.  
- Working with AI: share this document first, then point to relevant modules/files so AI contributors can ramp up quickly.
