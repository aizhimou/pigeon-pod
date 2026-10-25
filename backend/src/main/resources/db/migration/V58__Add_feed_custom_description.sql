-- Add custom description override for feeds
ALTER TABLE channel ADD COLUMN custom_description TEXT NULL;
ALTER TABLE playlist ADD COLUMN custom_description TEXT NULL;
