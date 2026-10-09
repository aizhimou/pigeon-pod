ALTER TABLE system_config
    ADD COLUMN unified_feed_custom_title TEXT NULL;

ALTER TABLE system_config
    ADD COLUMN unified_feed_custom_cover_ext TEXT NULL;

ALTER TABLE system_config
    ADD COLUMN unified_feed_language TEXT NULL;

ALTER TABLE system_config
    ADD COLUMN unified_feed_updated_at TIMESTAMP NULL;
