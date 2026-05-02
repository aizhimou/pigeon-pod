ALTER TABLE episode
    ADD COLUMN download_started_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_episode_downloading_started_at
    ON episode (download_status, download_started_at);
