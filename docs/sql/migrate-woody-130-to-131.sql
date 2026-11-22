-- Escolha do owner: manter The Woody Woodpecker Show (TVDB 76964).
-- Reparo específico desta instalação. Backup obrigatório.
-- Simula por padrão; psql -v apply=true aplica.
\set ON_ERROR_STOP on
\if :{?apply}
\else
\set apply false
\endif
BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE tv_shows, tv_show_titles, tv_show_images, tv_episodes, tv_episode_watches,
    tv_season_images, tv_season_covers, show_credits, show_term_assignments,
    show_list_items, show_lists, media_comments, media_ratings IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE woody_watch_mapping ON COMMIT DROP AS
WITH numbering(old_season, old_number, new_season, new_number) AS (
    VALUES (1941,7,1941,1), (1941,9,1941,2), (1941,12,1941,3), (1942,2,1942,1)
)
SELECT old.id AS old_episode_id, target.id AS target_episode_id
FROM numbering m
JOIN tv_episodes old ON old.show_id=130 AND old.season_number=m.old_season AND old.episode_number=m.old_number
JOIN tv_episodes target ON target.show_id=131 AND target.season_number=m.new_season AND target.episode_number=m.new_number;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tv_shows WHERE id=130 AND tvdb_id='236591'
        AND original_title='Woody Woodpecker and Friends' AND year=1929
        AND tmdb_id IS NULL AND imdb_id IS NULL AND NOT favorite AND NOT abandoned AND credits_curated_at IS NULL)
        OR NOT EXISTS (SELECT 1 FROM tv_shows WHERE id=131 AND tvdb_id='76964'
            AND tmdb_id='331442' AND imdb_id='tt0184175')
    THEN RAISE EXCEPTION 'Identidade/curadoria mudou; revisar'; END IF;
    IF (SELECT count(*) FROM woody_watch_mapping)<>4
        OR (SELECT count(DISTINCT old_episode_id) FROM woody_watch_mapping)<>4
        OR (SELECT count(DISTINCT target_episode_id) FROM woody_watch_mapping)<>4
        OR (SELECT count(*) FROM tv_episodes WHERE show_id=130)<>25
        OR (SELECT count(*) FROM tv_episodes WHERE show_id=131)<>25
        OR (SELECT count(*) FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id WHERE e.show_id=130)<>4
        OR EXISTS (SELECT 1 FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id
            WHERE e.show_id=130 AND NOT EXISTS (SELECT 1 FROM woody_watch_mapping m WHERE m.old_episode_id=e.id))
        OR EXISTS (SELECT 1 FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id WHERE e.show_id=131)
    THEN RAISE EXCEPTION 'Mapeamento ou histórico mudou; revisar'; END IF;
    IF EXISTS (SELECT 1 FROM tv_season_images WHERE show_id=130)
        OR EXISTS (SELECT 1 FROM tv_season_covers WHERE show_id=130)
        OR EXISTS (SELECT 1 FROM show_credits WHERE show_id=130)
        OR EXISTS (SELECT 1 FROM show_term_assignments WHERE show_id=130)
        OR EXISTS (SELECT 1 FROM show_list_items WHERE show_id=130)
        OR EXISTS (SELECT 1 FROM show_lists WHERE cover_show_id=130)
        OR EXISTS (SELECT 1 FROM media_comments WHERE (entity_type='SHOW' AND entity_id=130)
            OR (entity_type='EPISODE' AND entity_id IN (SELECT id FROM tv_episodes WHERE show_id=130)))
        OR EXISTS (SELECT 1 FROM media_ratings WHERE (entity_type='SHOW' AND entity_id=130)
            OR (entity_type='EPISODE' AND entity_id IN (SELECT id FROM tv_episodes WHERE show_id=130)))
    THEN RAISE EXCEPTION 'Vínculos adicionais no cadastro antigo; revisar'; END IF;
END $$;

-- Preserva IDs dos eventos, fontes, datas assistidas e datas de criação.
UPDATE tv_episode_watches w SET episode_id=m.target_episode_id
FROM woody_watch_mapping m WHERE w.episode_id=m.old_episode_id;

INSERT INTO tv_show_titles (show_id,title,locale,source,is_primary,created_at)
SELECT 131,title,locale,source,false,created_at FROM tv_show_titles WHERE show_id=130
ON CONFLICT (show_id,title,source) DO NOTHING;

DELETE FROM tv_shows WHERE id=130;
SELECT w.id,e.show_id,e.season_number,e.episode_number,e.title,w.source,w.watched_at
FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id WHERE e.show_id=131 ORDER BY w.id;
\if :apply
COMMIT;
\echo 'Histórico migrado e cadastro antigo removido.'
\else
ROLLBACK;
\echo 'Simulação desfeita.'
\endif
