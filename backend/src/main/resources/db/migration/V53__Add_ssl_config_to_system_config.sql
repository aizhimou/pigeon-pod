ALTER TABLE system_config ADD COLUMN ssl_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE system_config ADD COLUMN ssl_port INTEGER;
ALTER TABLE system_config ADD COLUMN ssl_certificate_path TEXT;
ALTER TABLE system_config ADD COLUMN ssl_key_path TEXT;
ALTER TABLE system_config ADD COLUMN https_only INTEGER NOT NULL DEFAULT 0;
