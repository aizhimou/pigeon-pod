CREATE TABLE IF NOT EXISTS feed_defaults
(
    id                          INTEGER PRIMARY KEY,
    auto_download_limit         INTEGER   NULL,
    auto_download_delay_minutes INTEGER   NULL DEFAULT 0,
    maximum_episodes            INTEGER   NULL,
    audio_quality               INTEGER   NULL,
    download_type               VARCHAR(20) NULL,
    video_quality               VARCHAR(20) NULL,
    video_encoding              VARCHAR(20) NULL,
    subtitle_languages          VARCHAR(200) NULL,
    subtitle_format             VARCHAR(10) NULL,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO feed_defaults (
    id,
    auto_download_limit,
    auto_download_delay_minutes,
    maximum_episodes,
    audio_quality,
    download_type,
    video_quality,
    video_encoding,
    subtitle_languages,
    subtitle_format
)
SELECT 0,
       3,
       0,
       u.default_maximum_episodes,
       NULL,
       'AUDIO',
       NULL,
       NULL,
       COALESCE(NULLIF(TRIM(u.subtitle_languages), ''), 'zh,en'),
       COALESCE(NULLIF(TRIM(u.subtitle_format), ''), 'vtt')
FROM user u
WHERE u.id = 0
  AND NOT EXISTS (SELECT 1 FROM feed_defaults WHERE id = 0);

INSERT INTO feed_defaults (
    id,
    auto_download_limit,
    auto_download_delay_minutes,
    maximum_episodes,
    audio_quality,
    download_type,
    video_quality,
    video_encoding,
    subtitle_languages,
    subtitle_format
)
SELECT 0,
       3,
       0,
       NULL,
       NULL,
       'AUDIO',
       NULL,
       NULL,
       'zh,en',
       'vtt'
WHERE NOT EXISTS (SELECT 1 FROM feed_defaults WHERE id = 0);
