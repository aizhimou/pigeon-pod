ALTER TABLE channel
    ADD COLUMN history_page_index INTEGER NOT NULL DEFAULT 0;

UPDATE channel
SET history_page_index = (
    -- Ceiling division: equivalent to CEIL(COUNT(*) / 50.0)
    SELECT (COUNT(*) + 49) / 50
    FROM episode
    WHERE episode.channel_id = channel.id
)
WHERE EXISTS (
    SELECT 1 FROM episode WHERE episode.channel_id = channel.id
);
