ALTER TABLE channel
    ADD COLUMN auto_download_delay_minutes INTEGER NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN auto_download_delay_minutes INTEGER NULL DEFAULT 0;

ALTER TABLE episode
    ADD COLUMN auto_download_after TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_episode_ready_auto_download_after
    ON episode (download_status, auto_download_after);
