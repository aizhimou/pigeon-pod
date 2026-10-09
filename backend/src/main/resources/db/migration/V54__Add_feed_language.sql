-- Add podcast language setting for feeds and feed defaults
ALTER TABLE channel ADD COLUMN language TEXT NULL;
ALTER TABLE playlist ADD COLUMN language TEXT NULL;
ALTER TABLE feed_defaults ADD COLUMN language TEXT NULL;
