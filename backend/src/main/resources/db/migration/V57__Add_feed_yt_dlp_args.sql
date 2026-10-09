-- Add custom yt-dlp args for feeds
ALTER TABLE channel ADD COLUMN yt_dlp_args TEXT NULL;
ALTER TABLE playlist ADD COLUMN yt_dlp_args TEXT NULL;
