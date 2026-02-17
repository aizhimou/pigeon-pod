ALTER TABLE playlist_episode
    ADD COLUMN source_channel_id TEXT NULL;

ALTER TABLE playlist_episode
    ADD COLUMN source_channel_name TEXT NULL;

ALTER TABLE playlist_episode
    ADD COLUMN source_channel_url TEXT NULL;
