-- Durable queue; services derive tags and perform remote I/O after commit.
ALTER TABLE movies
    ADD COLUMN collection_tags_revision BIGINT NOT NULL DEFAULT 1 CHECK (collection_tags_revision > 0),
    ADD COLUMN collection_tags_pending BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN collection_tags_message TEXT,
    ADD COLUMN collection_tags_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN collection_tags_claim_token UUID,
    ADD COLUMN collection_tags_claim_until TIMESTAMPTZ;
CREATE INDEX idx_movies_collection_tags_pending ON movies(collection_tags_next_attempt_at, id) WHERE collection_tags_pending;

CREATE FUNCTION invalidate_movie_collection_tags(target_id BIGINT) RETURNS void LANGUAGE sql AS $$
    UPDATE movies SET collection_tags_revision = collection_tags_revision + 1,
        collection_tags_pending = true, collection_tags_message = NULL, collection_tags_next_attempt_at = now(),
        collection_tags_claim_token = NULL, collection_tags_claim_until = NULL WHERE id = target_id;
$$;

CREATE FUNCTION movie_collection_reference_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM invalidate_movie_collection_tags(NEW.id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER movie_collection_reference_tag_change AFTER UPDATE OF collection_id, cover_url ON movies
FOR EACH ROW WHEN (OLD.collection_id IS DISTINCT FROM NEW.collection_id OR OLD.cover_url IS DISTINCT FROM NEW.cover_url)
EXECUTE FUNCTION movie_collection_reference_tag_change();

CREATE FUNCTION movie_collection_name_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target_id BIGINT;
BEGIN
    FOR target_id IN SELECT id FROM movies WHERE collection_id = NEW.id ORDER BY id LOOP
        PERFORM invalidate_movie_collection_tags(target_id);
    END LOOP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER movie_collection_name_tag_change AFTER UPDATE OF name ON movie_collections
FOR EACH ROW WHEN (OLD.name IS DISTINCT FROM NEW.name) EXECUTE FUNCTION movie_collection_name_tag_change();

CREATE FUNCTION movie_image_collection_tag_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM invalidate_movie_collection_tags(OLD.movie_id);
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        PERFORM invalidate_movie_collection_tags(LEAST(OLD.movie_id, NEW.movie_id));
        IF OLD.movie_id <> NEW.movie_id THEN PERFORM invalidate_movie_collection_tags(GREATEST(OLD.movie_id, NEW.movie_id)); END IF;
    ELSE
        PERFORM invalidate_movie_collection_tags(NEW.movie_id);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER movie_image_collection_tag_change AFTER INSERT OR UPDATE OF url, movie_id OR DELETE ON movie_images
FOR EACH ROW EXECUTE FUNCTION movie_image_collection_tag_change();
