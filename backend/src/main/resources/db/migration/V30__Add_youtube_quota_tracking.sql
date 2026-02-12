ALTER TABLE user
    ADD COLUMN youtube_daily_limit_units INTEGER NULL;

CREATE TABLE IF NOT EXISTS youtube_api_daily_usage
(
    usage_date_pt     TEXT PRIMARY KEY,
    request_count     INTEGER   NOT NULL DEFAULT 0,
    quota_units       INTEGER   NOT NULL DEFAULT 0,
    auto_sync_blocked INTEGER   NOT NULL DEFAULT 0,
    blocked_reason    TEXT      NULL,
    blocked_at        TIMESTAMP NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS youtube_api_daily_usage_method
(
    usage_date_pt TEXT      NOT NULL,
    api_method    TEXT      NOT NULL,
    request_count INTEGER   NOT NULL DEFAULT 0,
    quota_units   INTEGER   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (usage_date_pt, api_method)
);

CREATE INDEX IF NOT EXISTS idx_youtube_api_daily_usage_method_date
    ON youtube_api_daily_usage_method (usage_date_pt);
