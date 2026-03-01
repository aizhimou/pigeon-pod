ALTER TABLE feed_defaults
    ADD COLUMN minimum_duration INTEGER NULL;

UPDATE feed_defaults
SET minimum_duration = minimum_duration * 60
WHERE minimum_duration IS NOT NULL;

UPDATE channel
SET minimum_duration = minimum_duration * 60
WHERE minimum_duration IS NOT NULL;

UPDATE playlist
SET minimum_duration = minimum_duration * 60
WHERE minimum_duration IS NOT NULL;
