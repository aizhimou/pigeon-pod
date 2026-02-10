UPDATE channel
SET title_contain_keywords = REPLACE(title_contain_keywords, ' ', ',')
WHERE title_contain_keywords IS NOT NULL
  AND title_contain_keywords LIKE '% %';

UPDATE channel
SET title_exclude_keywords = REPLACE(title_exclude_keywords, ' ', ',')
WHERE title_exclude_keywords IS NOT NULL
  AND title_exclude_keywords LIKE '% %';

UPDATE channel
SET description_contain_keywords = REPLACE(description_contain_keywords, ' ', ',')
WHERE description_contain_keywords IS NOT NULL
  AND description_contain_keywords LIKE '% %';

UPDATE channel
SET description_exclude_keywords = REPLACE(description_exclude_keywords, ' ', ',')
WHERE description_exclude_keywords IS NOT NULL
  AND description_exclude_keywords LIKE '% %';

UPDATE playlist
SET title_contain_keywords = REPLACE(title_contain_keywords, ' ', ',')
WHERE title_contain_keywords IS NOT NULL
  AND title_contain_keywords LIKE '% %';

UPDATE playlist
SET title_exclude_keywords = REPLACE(title_exclude_keywords, ' ', ',')
WHERE title_exclude_keywords IS NOT NULL
  AND title_exclude_keywords LIKE '% %';

UPDATE playlist
SET description_contain_keywords = REPLACE(description_contain_keywords, ' ', ',')
WHERE description_contain_keywords IS NOT NULL
  AND description_contain_keywords LIKE '% %';

UPDATE playlist
SET description_exclude_keywords = REPLACE(description_exclude_keywords, ' ', ',')
WHERE description_exclude_keywords IS NOT NULL
  AND description_exclude_keywords LIKE '% %';
