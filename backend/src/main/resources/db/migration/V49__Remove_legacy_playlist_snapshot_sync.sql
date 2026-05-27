DROP TABLE IF EXISTS playlist_episode_detail_retry;

ALTER TABLE playlist
    DROP COLUMN last_snapshot_at;

ALTER TABLE playlist
    DROP COLUMN last_snapshot_size;

ALTER TABLE playlist
    DROP COLUMN last_sync_added_count;

ALTER TABLE playlist
    DROP COLUMN last_sync_removed_count;

ALTER TABLE playlist
    DROP COLUMN last_sync_moved_count;

ALTER TABLE playlist
    DROP COLUMN last_observed_item_count;

ALTER TABLE playlist
    DROP COLUMN last_item_count_checked_at;
