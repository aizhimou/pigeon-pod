CREATE TABLE IF NOT EXISTS youtube_playlist_item
(
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    playlist_id            TEXT      NOT NULL,
    playlist_item_id       TEXT      NOT NULL,
    video_id               TEXT      NOT NULL,
    episode_id             TEXT      NULL,
    item_added_at          TIMESTAMP NULL,
    video_published_at     TIMESTAMP NULL,
    position               INTEGER   NULL,
    item_privacy_status    TEXT      NULL,
    source_channel_id      TEXT      NULL,
    source_channel_name    TEXT      NULL,
    source_channel_url     TEXT      NULL,
    presence_status        TEXT      NOT NULL,
    materialization_status TEXT      NOT NULL,
    auto_dispatch_status   TEXT      NOT NULL,
    first_seen_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at             TIMESTAMP NULL,
    last_error             TEXT      NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_youtube_playlist_item_playlist_item
    ON youtube_playlist_item (playlist_id, playlist_item_id);

CREATE INDEX IF NOT EXISTS idx_youtube_playlist_item_presence_position
    ON youtube_playlist_item (playlist_id, presence_status, position);

CREATE INDEX IF NOT EXISTS idx_youtube_playlist_item_video
    ON youtube_playlist_item (playlist_id, video_id);

CREATE INDEX IF NOT EXISTS idx_youtube_playlist_item_materialization
    ON youtube_playlist_item (playlist_id, presence_status, materialization_status);

CREATE INDEX IF NOT EXISTS idx_youtube_playlist_item_dispatch
    ON youtube_playlist_item (playlist_id, auto_dispatch_status, materialization_status);

ALTER TABLE playlist
    ADD COLUMN last_observed_item_count INTEGER NULL;

ALTER TABLE playlist
    ADD COLUMN last_item_count_checked_at TIMESTAMP NULL;

ALTER TABLE playlist
    ADD COLUMN last_full_scan_at TIMESTAMP NULL;

ALTER TABLE playlist
    ADD COLUMN last_full_scan_size INTEGER NULL;

ALTER TABLE playlist
    ADD COLUMN last_full_scan_pages INTEGER NULL;

ALTER TABLE playlist
    ADD COLUMN bootstrap_completed_at TIMESTAMP NULL;

ALTER TABLE playlist
    ADD COLUMN last_sync_inserted_item_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN last_sync_removed_item_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN last_sync_moved_item_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN last_sync_materialized_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN last_sync_dispatched_item_count INTEGER NOT NULL DEFAULT 0;
