ALTER TABLE episode
    ADD COLUMN duration_seconds INTEGER NULL;

ALTER TABLE channel
    ADD COLUMN history_cursor_type TEXT NULL;

ALTER TABLE channel
    ADD COLUMN history_cursor_value TEXT NULL;

ALTER TABLE channel
    ADD COLUMN history_cursor_page INTEGER NULL;

ALTER TABLE channel
    ADD COLUMN history_cursor_exhausted INTEGER NOT NULL DEFAULT 0;

ALTER TABLE channel
    ADD COLUMN history_cursor_updated_at TIMESTAMP NULL;

ALTER TABLE playlist
    ADD COLUMN history_cursor_type TEXT NULL;

ALTER TABLE playlist
    ADD COLUMN history_cursor_value TEXT NULL;

ALTER TABLE playlist
    ADD COLUMN history_cursor_page INTEGER NULL;

ALTER TABLE playlist
    ADD COLUMN history_cursor_exhausted INTEGER NOT NULL DEFAULT 0;

ALTER TABLE playlist
    ADD COLUMN history_cursor_updated_at TIMESTAMP NULL;
