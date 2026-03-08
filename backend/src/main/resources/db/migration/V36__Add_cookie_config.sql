CREATE TABLE IF NOT EXISTS cookie_config
(
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    platform        TEXT                                NOT NULL,
    cookies_content TEXT                                NULL,
    enabled         INTEGER                             NOT NULL DEFAULT 1,
    source_type     TEXT                                NOT NULL DEFAULT 'UPLOAD',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(platform)
);

INSERT INTO cookie_config (platform, cookies_content, enabled, source_type, created_at, updated_at)
SELECT 'YOUTUBE',
       sc.cookies_content,
       1,
       'UPLOAD',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM system_config sc
WHERE sc.id = 0
  AND sc.cookies_content IS NOT NULL
  AND TRIM(sc.cookies_content) <> ''
  AND NOT EXISTS (SELECT 1 FROM cookie_config WHERE platform = 'YOUTUBE');
