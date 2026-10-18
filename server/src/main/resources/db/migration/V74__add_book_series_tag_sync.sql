-- Durable invalidation queue. Tag derivation and remote I/O remain in the service layer.
ALTER TABLE books
    ADD COLUMN series_tags_revision BIGINT NOT NULL DEFAULT 1 CHECK (series_tags_revision > 0),
    ADD COLUMN series_tags_pending BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN series_tags_message TEXT,
    ADD COLUMN series_tags_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN series_tags_claim_token UUID,
    ADD COLUMN series_tags_claim_until TIMESTAMPTZ;
CREATE INDEX idx_books_series_tags_pending ON books(series_tags_next_attempt_at, id) WHERE series_tags_pending;

CREATE FUNCTION invalidate_book_series_tags(target_id BIGINT) RETURNS void LANGUAGE sql AS $$
    UPDATE books SET series_tags_revision = series_tags_revision + 1,
        series_tags_pending = true, series_tags_message = NULL, series_tags_next_attempt_at = now(),
        series_tags_claim_token = NULL, series_tags_claim_until = NULL WHERE id = target_id;
$$;

CREATE FUNCTION book_series_membership_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM invalidate_book_series_tags(OLD.book_id);
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        PERFORM invalidate_book_series_tags(LEAST(OLD.book_id, NEW.book_id));
        IF OLD.book_id <> NEW.book_id THEN PERFORM invalidate_book_series_tags(GREATEST(OLD.book_id, NEW.book_id)); END IF;
    ELSE
        PERFORM invalidate_book_series_tags(NEW.book_id);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER book_series_membership_tag_change
AFTER INSERT OR UPDATE OF book_id, series_id OR DELETE ON book_series_items FOR EACH ROW EXECUTE FUNCTION book_series_membership_tag_change();

CREATE FUNCTION book_series_name_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target_id BIGINT;
BEGIN
    FOR target_id IN SELECT book_id FROM book_series_items WHERE series_id = NEW.id ORDER BY book_id LOOP
        PERFORM invalidate_book_series_tags(target_id);
    END LOOP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER book_series_name_tag_change AFTER UPDATE OF name ON book_series
FOR EACH ROW WHEN (OLD.name IS DISTINCT FROM NEW.name) EXECUTE FUNCTION book_series_name_tag_change();

CREATE FUNCTION book_cover_series_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM invalidate_book_series_tags(NEW.id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER book_cover_series_tag_change AFTER UPDATE OF cover_url ON books
FOR EACH ROW WHEN (OLD.cover_url IS DISTINCT FROM NEW.cover_url) EXECUTE FUNCTION book_cover_series_tag_change();

CREATE FUNCTION book_edition_series_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.cover_url IS NOT NULL THEN PERFORM invalidate_book_series_tags(OLD.book_id); END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.cover_url IS NOT NULL THEN PERFORM invalidate_book_series_tags(NEW.book_id); END IF;
    ELSIF OLD.cover_url IS DISTINCT FROM NEW.cover_url OR OLD.book_id IS DISTINCT FROM NEW.book_id THEN
        PERFORM invalidate_book_series_tags(LEAST(OLD.book_id, NEW.book_id));
        IF OLD.book_id <> NEW.book_id THEN PERFORM invalidate_book_series_tags(GREATEST(OLD.book_id, NEW.book_id)); END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER book_edition_series_tag_change AFTER INSERT OR UPDATE OF cover_url, book_id OR DELETE
ON book_editions FOR EACH ROW EXECUTE FUNCTION book_edition_series_tag_change();
