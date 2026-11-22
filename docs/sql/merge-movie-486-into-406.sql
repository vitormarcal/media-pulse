-- Reparo específico desta instalação: The Nightmare Before Christmas (1993).
-- Faça backup antes. Por padrão, simula e desfaz; use psql -v apply=true para aplicar.
\set ON_ERROR_STOP on
\if :{?apply}
\else
\set apply false
\endif

BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE movies, movie_titles, movie_images, movie_watches, movie_credits,
    movie_list_items, movie_lists, movie_term_assignments,
    movie_company_assignments IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM movies WHERE id = 406 AND year = 1993
            AND tmdb_id = '9479' AND imdb_id = 'tt0107688'
    ) OR NOT EXISTS (
        SELECT 1 FROM movies WHERE id = 486 AND year = 1993
            AND original_title = 'The Nightmare Before Christmas'
            AND tmdb_id IS NULL AND imdb_id IS NULL
            AND NOT favorite AND NOT abandoned AND credits_curated_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Identidade ou curadoria mudou; revisar antes do reparo';
    END IF;
    IF EXISTS (SELECT 1 FROM movie_watches WHERE movie_id = 486)
        OR EXISTS (SELECT 1 FROM movie_credits WHERE movie_id = 486)
        OR EXISTS (SELECT 1 FROM movie_list_items WHERE movie_id = 486)
        OR EXISTS (SELECT 1 FROM movie_lists WHERE cover_movie_id = 486)
        OR EXISTS (SELECT 1 FROM movie_term_assignments WHERE movie_id = 486)
        OR EXISTS (SELECT 1 FROM movie_company_assignments WHERE movie_id = 486)
    THEN
        RAISE EXCEPTION 'Duplicado ganhou vínculos; revisar a mesclagem';
    END IF;
END $$;

-- Preserva a capa e os títulos principais do canônico.
INSERT INTO movie_titles (movie_id, title, locale, source, is_primary, created_at)
SELECT 406, title, locale, source, false, created_at
FROM movie_titles WHERE movie_id = 486
ON CONFLICT (movie_id, title, source) DO NOTHING;

INSERT INTO movie_images (movie_id, url, is_primary, created_at)
SELECT 406, url, false, created_at
FROM movie_images WHERE movie_id = 486
ON CONFLICT (movie_id, url) DO NOTHING;

-- Remove apenas o registro duplicado; não apaga arquivos nem assets remotos.
DELETE FROM movies WHERE id = 486;

SELECT id, original_title, year, tmdb_id, imdb_id FROM movies WHERE id IN (406, 486);

\if :apply
COMMIT;
\echo 'Reparo aplicado.'
\else
ROLLBACK;
\echo 'Simulação desfeita. Para aplicar, execute com -v apply=true.'
\endif
