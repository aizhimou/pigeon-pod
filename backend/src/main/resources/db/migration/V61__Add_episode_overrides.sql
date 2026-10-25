-- Per-feed format for episode descriptions in RSS (NULL keeps PigeonPod's default)
ALTER TABLE channel ADD COLUMN episode_description_template TEXT NULL;
ALTER TABLE playlist ADD COLUMN episode_description_template TEXT NULL;

-- Per-episode overrides for title, description and thumbnail
ALTER TABLE episode ADD COLUMN custom_title TEXT NULL;
ALTER TABLE episode ADD COLUMN custom_description TEXT NULL;
ALTER TABLE episode ADD COLUMN custom_cover_ext TEXT NULL;
ALTER TABLE episode ADD COLUMN customized_at DATETIME NULL;
