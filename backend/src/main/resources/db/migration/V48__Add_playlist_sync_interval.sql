ALTER TABLE playlist
    ADD COLUMN sync_interval_hours INTEGER NOT NULL DEFAULT 3;
