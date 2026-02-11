ALTER TABLE playlist
    ADD COLUMN last_snapshot_at TIMESTAMP NULL;

ALTER TABLE playlist
    ADD COLUMN last_snapshot_size INTEGER NULL;

ALTER TABLE playlist
    ADD COLUMN last_sync_added_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN last_sync_removed_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN last_sync_moved_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN sync_error TEXT NULL;

ALTER TABLE playlist
    ADD COLUMN sync_error_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS playlist_episode_detail_retry
(
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    playlist_id           TEXT      NOT NULL,
    episode_id            TEXT      NOT NULL,
    position              INTEGER   NULL,
    approximate_published_at TIMESTAMP NULL,
    retry_count           INTEGER   NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error            TEXT      NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_playlist_episode_detail_retry_playlist_episode
    ON playlist_episode_detail_retry (playlist_id, episode_id);

CREATE INDEX IF NOT EXISTS idx_playlist_episode_detail_retry_next_retry_at
    ON playlist_episode_detail_retry (next_retry_at);

