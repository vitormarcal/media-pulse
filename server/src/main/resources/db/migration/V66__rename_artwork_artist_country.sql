-- Keep the existing values for owner review; do not infer origin from nationality.
ALTER TABLE artwork_artists RENAME COLUMN nationality TO country_of_origin;
