ALTER TABLE book_series
    ADD COLUMN description TEXT,
    ADD COLUMN hardcover_slug TEXT,
    ADD COLUMN hardcover_author_id BIGINT,
    ADD COLUMN hardcover_author_name TEXT,
    ADD COLUMN hardcover_is_completed BOOLEAN,
    ADD COLUMN hardcover_books_count INTEGER CHECK (hardcover_books_count >= 0),
    ADD COLUMN hardcover_primary_books_count INTEGER CHECK (hardcover_primary_books_count >= 0);
CREATE INDEX idx_book_authors_author_role ON book_authors(author_id, role, book_id);
