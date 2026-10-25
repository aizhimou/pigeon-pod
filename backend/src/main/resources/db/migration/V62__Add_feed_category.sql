-- Podcast category for RSS (<itunes:category>), stored as "Category" or "Category|Subcategory".
-- NULL keeps the previous default, Technology.
ALTER TABLE channel ADD COLUMN category TEXT NULL;
ALTER TABLE playlist ADD COLUMN category TEXT NULL;
