ALTER TABLE authors ADD COLUMN hardcover_import_id BIGINT UNIQUE CHECK (hardcover_import_id > 0);

-- Preserve local IDs, names, fingerprints and every association. Only promote unique evidence.
UPDATE authors a
SET hardcover_import_id = c.hardcover_id
FROM hardcover_author_candidates c
WHERE c.author_id = a.id AND c.hardcover_id > 0
  AND NOT EXISTS (
      SELECT 1 FROM hardcover_author_candidates other
      WHERE other.author_id = c.author_id AND other.hardcover_id <> c.hardcover_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM hardcover_author_candidates other
      WHERE other.hardcover_id = c.hardcover_id AND other.author_id <> c.author_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM book_author_profiles p
      WHERE p.author_id = a.id AND p.managed_manually AND p.hardcover_id <> c.hardcover_id
  );
