ALTER TABLE channel RENAME COLUMN initial_episodes TO auto_download_limit;
ALTER TABLE playlist RENAME COLUMN initial_episodes TO auto_download_limit;

ALTER TABLE channel RENAME COLUMN sync_state TO auto_download_enabled;
ALTER TABLE playlist RENAME COLUMN sync_state TO auto_download_enabled;
