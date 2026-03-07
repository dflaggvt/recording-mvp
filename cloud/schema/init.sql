-- MemoryStream Cloud Schema
-- Cloud SQL Postgres with pgvector extension

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Chunks ──────────────────────────────────────────────────────────────────

CREATE TABLE memory_chunks (
    id              TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id         TEXT NOT NULL DEFAULT 'default',
    start_timestamp BIGINT NOT NULL,
    end_timestamp   BIGINT NOT NULL,
    transcript      TEXT,
    summary         TEXT,
    commitments     TEXT,
    embedding       vector(1536),
    audio_file_path TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING_TRANSCRIPTION',
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    place_name      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chunks_user_status ON memory_chunks(user_id, status);
CREATE INDEX idx_chunks_user_time ON memory_chunks(user_id, start_timestamp DESC);

-- ── Utterances ──────────────────────────────────────────────────────────────

CREATE TABLE utterances (
    id                      TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    chunk_id                TEXT REFERENCES memory_chunks(id) ON DELETE CASCADE,
    timestamp               BIGINT NOT NULL,
    end_timestamp           BIGINT,
    text                    TEXT NOT NULL,
    embedding               vector(1536),
    is_embedded             BOOLEAN NOT NULL DEFAULT false,
    speaker_id              TEXT,
    diarization_label       INTEGER,
    consolidated_speaker_id TEXT
);

CREATE INDEX idx_utterances_chunk ON utterances(chunk_id);
CREATE INDEX idx_utterances_time ON utterances(timestamp);

-- ── Speakers ────────────────────────────────────────────────────────────────

CREATE TABLE speakers (
    id          TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id     TEXT NOT NULL DEFAULT 'default',
    name        TEXT NOT NULL,
    voiceprint  REAL[],
    is_primary  BOOLEAN NOT NULL DEFAULT false,
    enrolled_at BIGINT NOT NULL,
    color       INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_speakers_user ON speakers(user_id);

-- ── Claims ──────────────────────────────────────────────────────────────────

CREATE TABLE claims (
    id          TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    chunk_id    TEXT NOT NULL REFERENCES memory_chunks(id) ON DELETE CASCADE,
    speaker_id  TEXT,
    speaker_name TEXT,
    timestamp   BIGINT NOT NULL,
    topic       TEXT NOT NULL,
    claim_text  TEXT NOT NULL,
    raw_quote   TEXT NOT NULL,
    type        TEXT NOT NULL,
    place_name  TEXT,
    embedding   vector(1536),
    created_at  BIGINT NOT NULL
);

CREATE INDEX idx_claims_chunk ON claims(chunk_id);
CREATE INDEX idx_claims_topic ON claims(topic);

-- ── Insights ────────────────────────────────────────────────────────────────

CREATE TABLE insights (
    id               TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    type             TEXT NOT NULL,
    title            TEXT NOT NULL,
    body             TEXT NOT NULL,
    source_timestamp BIGINT NOT NULL,
    created_at       BIGINT NOT NULL,
    expires_at       BIGINT,
    dismissed_at     BIGINT,
    notified_at      BIGINT,
    place_hint       TEXT
);

CREATE INDEX idx_insights_type ON insights(type);
CREATE INDEX idx_insights_created ON insights(created_at DESC);

-- ── Known Places ────────────────────────────────────────────────────────────

CREATE TABLE known_places (
    id              TEXT PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    label           TEXT NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    radius_meters   DOUBLE PRECISION NOT NULL DEFAULT 150.0,
    visit_count     INTEGER NOT NULL DEFAULT 1,
    last_visited_at BIGINT NOT NULL,
    is_exclusion    BOOLEAN NOT NULL DEFAULT false,
    user_id         TEXT NOT NULL DEFAULT 'default'
);

-- ── Daily Summaries ─────────────────────────────────────────────────────────

CREATE TABLE daily_summaries (
    day_timestamp    BIGINT PRIMARY KEY,
    summary          TEXT NOT NULL,
    total_duration_ms BIGINT NOT NULL,
    chunk_count      INTEGER NOT NULL,
    places           TEXT,
    generated_at     BIGINT NOT NULL
);

-- ── Vector similarity search functions ──────────────────────────────────────

CREATE OR REPLACE FUNCTION search_chunks(
    query_embedding vector(1536),
    match_count INT DEFAULT 10,
    min_user_id TEXT DEFAULT 'default'
)
RETURNS TABLE (
    id TEXT,
    transcript TEXT,
    summary TEXT,
    start_timestamp BIGINT,
    place_name TEXT,
    similarity FLOAT
)
LANGUAGE sql STABLE
AS $$
    SELECT
        id, transcript, summary, start_timestamp, place_name,
        1 - (embedding <=> query_embedding) AS similarity
    FROM memory_chunks
    WHERE user_id = min_user_id
      AND embedding IS NOT NULL
    ORDER BY embedding <=> query_embedding
    LIMIT match_count;
$$;

CREATE OR REPLACE FUNCTION search_utterances(
    query_embedding vector(1536),
    match_count INT DEFAULT 20
)
RETURNS TABLE (
    id TEXT,
    chunk_id TEXT,
    text TEXT,
    "timestamp" BIGINT,
    speaker_id TEXT,
    similarity FLOAT
)
LANGUAGE sql STABLE
AS $$
    SELECT
        id, chunk_id, text, timestamp, speaker_id,
        1 - (embedding <=> query_embedding) AS similarity
    FROM utterances
    WHERE is_embedded = true
    ORDER BY embedding <=> query_embedding
    LIMIT match_count;
$$;
