ALTER TABLE episode
    ADD COLUMN next_retry_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_episode_failed_next_retry_at
    ON episode (download_status, next_retry_at, retry_number);

UPDATE episode
SET next_retry_at = CURRENT_TIMESTAMP
WHERE download_status = 'FAILED'
  AND next_retry_at IS NULL
  AND retry_number <= 5;
