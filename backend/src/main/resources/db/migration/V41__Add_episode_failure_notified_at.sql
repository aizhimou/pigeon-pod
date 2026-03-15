ALTER TABLE episode
    ADD COLUMN failure_notified_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_episode_failed_notification_candidates
    ON episode (download_status, retry_number, next_retry_at, failure_notified_at);
