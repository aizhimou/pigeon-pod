CREATE TABLE IF NOT EXISTS notification_config
(
    id                     INTEGER PRIMARY KEY CHECK (id = 0),

    email_enabled          INTEGER                             NOT NULL DEFAULT 0,
    email_host             TEXT                                NULL,
    email_port             INTEGER                             NULL,
    email_username         TEXT                                NULL,
    email_password         TEXT                                NULL,
    email_from             TEXT                                NULL,
    email_to               TEXT                                NULL,
    email_starttls_enabled INTEGER                             NOT NULL DEFAULT 1,
    email_ssl_enabled      INTEGER                             NOT NULL DEFAULT 0,

    webhook_enabled        INTEGER                             NOT NULL DEFAULT 0,
    webhook_url            TEXT                                NULL,
    webhook_custom_headers TEXT                                NULL,
    webhook_json_body      TEXT                                NULL,

    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO notification_config (id, created_at, updated_at)
SELECT 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM notification_config WHERE id = 0);
