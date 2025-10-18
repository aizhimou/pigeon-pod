ALTER TABLE channel RENAME COLUMN contain_keywords TO title_contain_keywords;
ALTER TABLE channel RENAME COLUMN exclude_keywords TO title_exclude_keywords;
ALTER TABLE channel ADD COLUMN description_contain_keywords TEXT NULL;
ALTER TABLE channel ADD COLUMN description_exclude_keywords TEXT NULL;

ALTER TABLE playlist RENAME COLUMN contain_keywords TO title_contain_keywords;
ALTER TABLE playlist RENAME COLUMN exclude_keywords TO title_exclude_keywords;
ALTER TABLE playlist ADD COLUMN description_contain_keywords TEXT NULL;
ALTER TABLE playlist ADD COLUMN description_exclude_keywords TEXT NULL;
