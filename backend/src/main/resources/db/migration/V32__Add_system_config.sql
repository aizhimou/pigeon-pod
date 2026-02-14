CREATE TABLE IF NOT EXISTS system_config
(
    id                          INTEGER PRIMARY KEY CHECK (id = 0),
    base_url                    TEXT                                NULL,

    youtube_api_key             TEXT                                NULL,
    cookies_content             TEXT                                NULL,
    yt_dlp_args                 TEXT                                NULL,
    login_captcha_enabled       INTEGER                             NOT NULL DEFAULT 0,
    youtube_daily_limit_units   INTEGER                             NULL,

    storage_type                TEXT                                NOT NULL DEFAULT 'LOCAL',
    storage_temp_dir            TEXT                                NOT NULL DEFAULT '/tmp/pigeon-pod',

    local_audio_path            TEXT                                NOT NULL DEFAULT '/data/audio/',
    local_video_path            TEXT                                NOT NULL DEFAULT '/data/video/',
    local_cover_path            TEXT                                NOT NULL DEFAULT '/data/cover/',

    s3_endpoint                 TEXT                                NULL,
    s3_region                   TEXT                                NULL DEFAULT 'us-east-1',
    s3_bucket                   TEXT                                NULL,
    s3_access_key               TEXT                                NULL,
    s3_secret_key               TEXT                                NULL,
    s3_path_style_access        INTEGER                             NOT NULL DEFAULT 1,
    s3_connect_timeout_seconds  INTEGER                             NOT NULL DEFAULT 30,
    s3_socket_timeout_seconds   INTEGER                             NOT NULL DEFAULT 1800,
    s3_read_timeout_seconds     INTEGER                             NOT NULL DEFAULT 1800,
    s3_presign_expire_hours     INTEGER                             NOT NULL DEFAULT 72,

    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO system_config (id,
                           youtube_api_key,
                           cookies_content,
                           yt_dlp_args,
                           login_captcha_enabled,
                           youtube_daily_limit_units,
                           created_at,
                           updated_at)
SELECT 0,
       u.youtube_api_key,
       u.cookies_content,
       u.yt_dlp_args,
       COALESCE(u.login_captcha_enabled, 0),
       u.youtube_daily_limit_units,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM user u
WHERE u.id = 0
  AND NOT EXISTS (SELECT 1 FROM system_config WHERE id = 0);

INSERT INTO system_config (id, created_at, updated_at)
SELECT 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE id = 0);

ALTER TABLE user drop column youtube_api_key;
ALTER TABLE user drop column cookies_content;
ALTER TABLE user drop column yt_dlp_args;
ALTER TABLE user drop column login_captcha_enabled;
ALTER TABLE user drop column youtube_daily_limit_units;
