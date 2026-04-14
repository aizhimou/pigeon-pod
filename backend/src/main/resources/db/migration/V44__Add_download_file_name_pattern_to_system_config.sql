ALTER TABLE system_config
    ADD COLUMN download_file_name_pattern TEXT NOT NULL DEFAULT '{title}';

UPDATE system_config
SET download_file_name_pattern = '{title}'
WHERE download_file_name_pattern IS NULL
   OR TRIM(download_file_name_pattern) = '';
