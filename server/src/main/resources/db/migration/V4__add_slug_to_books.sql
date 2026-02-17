ALTER TABLE books
ADD COLUMN IF NOT EXISTS slug TEXT;

WITH normalized AS (
  SELECT
    id,
    LEFT(
      TRIM(BOTH '_' FROM REGEXP_REPLACE(LOWER(COALESCE(title, '')), '[^a-z0-9]+', '_', 'g')),
      40
    ) AS safe_title
  FROM books
)
UPDATE books b
SET slug = CASE
  WHEN n.safe_title = '' THEN b.id::TEXT
  ELSE b.id::TEXT || '_' || n.safe_title
END
FROM normalized n
WHERE b.id = n.id
  AND (b.slug IS NULL OR b.slug = '');

UPDATE books
SET slug = id::TEXT
WHERE slug IS NULL OR slug = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_books_slug ON books(slug);

ALTER TABLE books
ALTER COLUMN slug SET NOT NULL;
