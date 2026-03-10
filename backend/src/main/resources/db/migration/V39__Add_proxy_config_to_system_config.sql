ALTER TABLE system_config
    ADD COLUMN proxy_enabled INTEGER NOT NULL DEFAULT 0;

ALTER TABLE system_config
    ADD COLUMN proxy_type TEXT NULL;

ALTER TABLE system_config
    ADD COLUMN proxy_host TEXT NULL;

ALTER TABLE system_config
    ADD COLUMN proxy_port INTEGER NULL;

ALTER TABLE system_config
    ADD COLUMN proxy_username TEXT NULL;

ALTER TABLE system_config
    ADD COLUMN proxy_password TEXT NULL;
