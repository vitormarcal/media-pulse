DELETE FROM movie_credits
WHERE credit_type = 'CREW'
  AND job NOT IN ('Director', 'Writer', 'Screenplay', 'Story');

DELETE FROM show_credits
WHERE credit_type = 'CREW'
  AND COALESCE(job, '') NOT IN ('Director', 'Writer', 'Screenplay', 'Story Editor');

DELETE FROM people p
WHERE p.favorited_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM movie_credits mc
      WHERE mc.person_id = p.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM show_credits sc
      WHERE sc.person_id = p.id
  );
