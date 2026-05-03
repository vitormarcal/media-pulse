ALTER TABLE movie_people RENAME TO people;

ALTER INDEX idx_movie_people_normalized_name RENAME TO idx_people_normalized_name;

ALTER TABLE movie_credits
    DROP CONSTRAINT IF EXISTS movie_credits_person_id_fkey;

ALTER TABLE movie_credits
    ADD CONSTRAINT movie_credits_person_id_fkey
        FOREIGN KEY (person_id) REFERENCES people(id) ON DELETE CASCADE;
