-- Add podcast author (itunes:author) for feeds
ALTER TABLE channel ADD COLUMN author TEXT NULL;
ALTER TABLE playlist ADD COLUMN author TEXT NULL;
