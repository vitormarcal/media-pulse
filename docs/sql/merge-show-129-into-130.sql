-- Reparo específico: O Show do Pica-Pau / Woody Woodpecker and Friends.
-- Backup obrigatório. Simula por padrão; psql -v apply=true aplica.
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

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM tv_shows WHERE id=129 AND original_title='O Show do Pica-Pau'
            AND slug='woody-woodpecker-and-friends' AND year IS NULL
            AND tmdb_id IS NULL AND tvdb_id IS NULL AND imdb_id IS NULL
            AND NOT favorite AND NOT abandoned AND credits_curated_at IS NULL
    ) OR NOT EXISTS (
        SELECT 1 FROM tv_shows WHERE id=130 AND tvdb_id='236591'
            AND slug='woody-woodpecker-and-friends' AND year=1929
    ) THEN
        RAISE EXCEPTION 'Identidade/curadoria mudou; revisar';
    END IF;
    IF EXISTS (SELECT 1 FROM tv_show_images WHERE show_id=129)
        OR EXISTS (SELECT 1 FROM tv_season_images WHERE show_id=129)
        OR EXISTS (SELECT 1 FROM tv_season_covers WHERE show_id=129)
        OR EXISTS (SELECT 1 FROM show_credits WHERE show_id=129)
        OR EXISTS (SELECT 1 FROM show_term_assignments WHERE show_id=129)
        OR EXISTS (SELECT 1 FROM show_list_items WHERE show_id=129)
        OR EXISTS (SELECT 1 FROM show_lists WHERE cover_show_id=129)
        OR EXISTS (SELECT 1 FROM media_comments WHERE (entity_type='SHOW' AND entity_id=129)
            OR (entity_type='EPISODE' AND entity_id IN (SELECT id FROM tv_episodes WHERE show_id=129)))
        OR EXISTS (SELECT 1 FROM media_ratings WHERE (entity_type='SHOW' AND entity_id=129)
            OR (entity_type='EPISODE' AND entity_id IN (SELECT id FROM tv_episodes WHERE show_id=129)))
    THEN
        RAISE EXCEPTION 'Novos vínculos no duplicado; revisar';
    END IF;
    IF (SELECT count(*) FROM tv_episodes WHERE show_id=129) <> 4
        OR (SELECT count(*) FROM tv_episodes WHERE show_id=130) <> 25
        OR (SELECT count(*) FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id WHERE e.show_id=129) <> 4
        OR EXISTS (SELECT 1 FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id WHERE e.show_id=130)
    THEN
        RAISE EXCEPTION 'Episódios/histórico mudaram; revisar';
    END IF;
    IF EXISTS (
        SELECT 1 FROM tv_episodes old WHERE old.show_id=129 AND (
            old.tmdb_id IS NOT NULL OR old.tvdb_id IS NOT NULL OR old.imdb_id IS NOT NULL
            OR (SELECT count(*) FROM tv_episodes target WHERE target.show_id=130
                AND target.season_number=old.season_number AND target.episode_number=old.episode_number
                AND target.title=old.title) <> 1
        )
    ) THEN
        RAISE EXCEPTION 'Correspondência de episódios deixou de ser exata e única';
    END IF;
END $$;

-- Atualiza os próprios registros de visualização, preservando IDs, fontes e datas.
UPDATE tv_episode_watches w SET episode_id=target.id
FROM tv_episodes old JOIN tv_episodes target
    ON target.show_id=130 AND target.season_number=old.season_number
    AND target.episode_number=old.episode_number AND target.title=old.title
WHERE old.show_id=129 AND w.episode_id=old.id;

INSERT INTO tv_show_titles (show_id,title,locale,source,is_primary,created_at)
SELECT 130,title,locale,source,false,created_at FROM tv_show_titles WHERE show_id=129
ON CONFLICT (show_id,title,source) DO NOTHING;

DELETE FROM tv_shows WHERE id=129;
SELECT s.id,s.original_title,
    (SELECT count(*) FROM tv_episodes e WHERE e.show_id=s.id) episodes,
    (SELECT count(*) FROM tv_episode_watches w JOIN tv_episodes e ON e.id=w.episode_id WHERE e.show_id=s.id) watches
FROM tv_shows s WHERE s.id IN (129,130);
\if :apply
COMMIT;
\echo 'Reparo aplicado.'
\else
ROLLBACK;
\echo 'Simulação desfeita.'
\endif
