-- User 表添加全局字幕配置（简化为两个字段）
ALTER TABLE user ADD COLUMN subtitle_languages VARCHAR(200) DEFAULT 'zh,en';
ALTER TABLE user ADD COLUMN subtitle_format VARCHAR(10) DEFAULT 'vtt';

-- Channel 表添加订阅级别字幕配置（nullable，null = 使用全局设置）
ALTER TABLE channel ADD COLUMN subtitle_languages VARCHAR(200);
ALTER TABLE channel ADD COLUMN subtitle_format VARCHAR(10);

-- Playlist 表添加订阅级别字幕配置（nullable）
ALTER TABLE playlist ADD COLUMN subtitle_languages VARCHAR(200);
ALTER TABLE playlist ADD COLUMN subtitle_format VARCHAR(10);

