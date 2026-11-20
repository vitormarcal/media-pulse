-- Curated works did not record which individual credits were chosen by the owner.
-- Preserve their entire existing set rather than guessing and losing personal choices.
ALTER TABLE movie_credits ADD COLUMN manually_selected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE show_credits ADD COLUMN manually_selected BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE movie_credits c SET manually_selected = TRUE
FROM movies m WHERE c.movie_id = m.id AND m.credits_curated_at IS NOT NULL;

UPDATE show_credits c SET manually_selected = TRUE
FROM tv_shows s WHERE c.show_id = s.id AND s.credits_curated_at IS NOT NULL;
