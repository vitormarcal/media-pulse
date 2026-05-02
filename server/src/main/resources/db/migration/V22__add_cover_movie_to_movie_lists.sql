ALTER TABLE movie_lists
    ADD COLUMN cover_movie_id BIGINT NULL REFERENCES movies(id) ON DELETE SET NULL;
