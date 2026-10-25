-- Add per-feed episode artwork style (JSON) for squaring episode covers in RSS
ALTER TABLE channel ADD COLUMN episode_cover_style TEXT NULL;
ALTER TABLE playlist ADD COLUMN episode_cover_style TEXT NULL;
