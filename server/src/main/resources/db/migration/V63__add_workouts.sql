CREATE TABLE workouts (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL CHECK (category IN ('RUNNING', 'JUMP_ROPE', 'GYM')),
    started_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    distance_km NUMERIC(10, 3),
    jumps INTEGER,
    location VARCHAR(200),
    photo_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT workout_distance CHECK (
        (category = 'RUNNING' AND distance_km IS NOT NULL AND distance_km > 0)
        OR (category <> 'RUNNING' AND distance_km IS NULL)
    ),
    CONSTRAINT workout_jumps CHECK (
        jumps IS NULL OR (category = 'JUMP_ROPE' AND jumps > 0)
    )
);
CREATE INDEX idx_workouts_history ON workouts (started_at DESC, id DESC);
CREATE INDEX idx_workouts_category_history ON workouts (category, started_at DESC, id DESC);
