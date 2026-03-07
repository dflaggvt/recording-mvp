"""
MemoryStream Cloud Run Service

Processing pipeline handlers (called by GCP Workflows):
  POST /process/transcribe
  POST /process/embed-chunk
  POST /process/extract-claims
  POST /process/detect-commitments
  POST /process/check-consistency
  POST /process/embed-utterances
  POST /process/proactive

App API endpoints (called by Android client):
  POST   /api/chunks              - create chunk record + trigger pipeline
  GET    /api/chunks              - list chunks
  GET    /api/chunks/{id}         - get chunk detail
  GET    /api/utterances          - get utterances for chunk
  GET    /api/speakers            - list speakers
  POST   /api/search              - semantic search
  POST   /api/upload-url          - get signed upload URL
  GET    /api/exclusion-zones     - list exclusion zones
  POST   /api/exclusion-zones     - create exclusion zone
  DELETE /api/exclusion-zones/{id} - delete exclusion zone
"""

import json
import logging
import os
import time
import uuid
from contextlib import asynccontextmanager
from datetime import timedelta

import google.auth
import google.auth.iam
import google.auth.transport.requests
import google.oauth2.service_account
import httpx
from fastapi import FastAPI, HTTPException, Query
from google.cloud import storage as gcs
from pydantic import BaseModel

import config
import db

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("memorystream")


# ── App lifecycle ────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    await db.init_pool()
    log.info("Database pool initialized")
    yield
    await db.close_pool()

app = FastAPI(title="MemoryStream", lifespan=lifespan)


# ── Models ───────────────────────────────────────────────────────────────────

class ChunkRequest(BaseModel):
    chunk_id: str | None = None
    user_id: str = "default"
    start_timestamp: int
    end_timestamp: int
    audio_gcs_path: str
    latitude: float | None = None
    longitude: float | None = None
    place_name: str | None = None


class ProcessRequest(BaseModel):
    chunk_id: str


class SearchRequest(BaseModel):
    query: str
    user_id: str = "default"
    limit: int = 10


# ── App API endpoints ────────────────────────────────────────────────────────

@app.post("/api/chunks")
async def create_chunk(req: ChunkRequest):
    chunk_id = req.chunk_id or str(uuid.uuid4())
    await db.execute(
        """INSERT INTO memory_chunks
           (id, user_id, start_timestamp, end_timestamp, audio_file_path, status,
            latitude, longitude, place_name)
           VALUES ($1, $2, $3, $4, $5, 'PENDING_TRANSCRIPTION', $6, $7, $8)""",
        chunk_id, req.user_id, req.start_timestamp, req.end_timestamp,
        req.audio_gcs_path, req.latitude, req.longitude, req.place_name,
    )
    log.info(f"Chunk {chunk_id} created, triggering workflow")

    # Trigger the processing workflow
    await trigger_workflow(chunk_id)

    return {"chunk_id": chunk_id, "status": "PENDING_TRANSCRIPTION"}


@app.get("/api/chunks")
async def list_chunks(
    user_id: str = "default",
    limit: int = Query(default=30, le=100),
    offset: int = 0,
):
    rows = await db.fetch_all(
        """SELECT id, start_timestamp, end_timestamp, transcript, summary,
                  status, place_name, latitude, longitude
           FROM memory_chunks
           WHERE user_id = $1
           ORDER BY start_timestamp DESC
           LIMIT $2 OFFSET $3""",
        user_id, limit, offset,
    )
    return [dict(r) for r in rows]


@app.get("/api/chunks/{chunk_id}")
async def get_chunk(chunk_id: str):
    row = await db.fetch_one(
        """SELECT id, start_timestamp, end_timestamp, transcript, summary,
                  commitments, status, place_name, latitude, longitude
           FROM memory_chunks WHERE id = $1""",
        chunk_id,
    )
    if not row:
        raise HTTPException(404, "Chunk not found")
    return dict(row)


@app.get("/api/utterances")
async def get_utterances(chunk_id: str):
    rows = await db.fetch_all(
        """SELECT id, chunk_id, timestamp, end_timestamp, text,
                  speaker_id, diarization_label, consolidated_speaker_id
           FROM utterances
           WHERE chunk_id = $1
           ORDER BY timestamp ASC""",
        chunk_id,
    )
    return [dict(r) for r in rows]


@app.get("/api/speakers")
async def list_speakers(user_id: str = "default"):
    rows = await db.fetch_all(
        """SELECT id, name, is_primary, enrolled_at, color
           FROM speakers
           WHERE user_id = $1
           ORDER BY is_primary DESC, enrolled_at ASC""",
        user_id,
    )
    return [dict(r) for r in rows]


@app.get("/api/insights")
async def list_insights(
    limit: int = Query(default=20, le=100),
    type: str | None = None,
    start: int | None = None,
    end: int | None = None,
    user_id: str = "default",
):
    conditions = ["dismissed_at IS NULL"]
    params: list = []
    idx = 1

    if type:
        conditions.append(f"type = ${idx}")
        params.append(type)
        idx += 1

    if start is not None:
        conditions.append(f"source_timestamp >= ${idx}")
        params.append(start)
        idx += 1

    if end is not None:
        conditions.append(f"source_timestamp <= ${idx}")
        params.append(end)
        idx += 1

    where = " AND ".join(conditions)
    params.append(limit)

    rows = await db.fetch_all(
        f"""SELECT id, type, title, body, source_timestamp, created_at, place_hint
           FROM insights
           WHERE {where}
           ORDER BY created_at DESC
           LIMIT ${idx}""",
        *params,
    )
    return [dict(r) for r in rows]


@app.put("/api/insights/{insight_id}/dismiss")
async def dismiss_insight(insight_id: str):
    now_ms = int(time.time() * 1000)
    result = await db.execute(
        "UPDATE insights SET dismissed_at = $1 WHERE id = $2",
        now_ms, insight_id,
    )
    if "UPDATE 0" in str(result):
        raise HTTPException(404, "Insight not found")
    return {"status": "dismissed"}


@app.get("/api/chunks/by-range")
async def get_chunks_by_range(
    start: int,
    end: int,
    user_id: str = "default",
):
    rows = await db.fetch_all(
        """SELECT id, start_timestamp, end_timestamp, transcript, summary,
                  status, place_name, latitude, longitude
           FROM memory_chunks
           WHERE user_id = $1 AND start_timestamp <= $3 AND end_timestamp >= $2
           ORDER BY start_timestamp ASC""",
        user_id, start, end,
    )
    return [dict(r) for r in rows]


@app.get("/api/daily-summaries")
async def get_daily_summaries(
    user_id: str = "default",
    limit: int = Query(default=14, le=100),
    offset: int = 0,
):
    rows = await db.fetch_all(
        """SELECT
               (start_timestamp / 86400000) * 86400000 AS day_timestamp,
               COUNT(*) AS chunk_count,
               SUM(end_timestamp - start_timestamp) AS total_duration_ms,
               STRING_AGG(DISTINCT place_name, ',') FILTER (WHERE place_name IS NOT NULL) AS places
           FROM memory_chunks
           WHERE user_id = $1 AND status = 'EMBEDDED'
           GROUP BY start_timestamp / 86400000
           ORDER BY day_timestamp DESC
           LIMIT $2 OFFSET $3""",
        user_id, limit, offset,
    )
    return [dict(r) for r in rows]


@app.post("/api/daily-summary/generate")
async def generate_daily_summary(day_timestamp: int, user_id: str = "default"):
    day_end = day_timestamp + 86400000 - 1
    chunks = await db.fetch_all(
        """SELECT transcript, summary, place_name, start_timestamp
           FROM memory_chunks
           WHERE user_id = $1 AND start_timestamp >= $2 AND start_timestamp <= $3
             AND transcript IS NOT NULL
           ORDER BY start_timestamp ASC""",
        user_id, day_timestamp, day_end,
    )
    if not chunks:
        return {"narrative": None}

    context = "\n\n".join(
        f"[{r['place_name'] or 'Unknown'}] {r['transcript'][:500]}"
        for r in chunks
    )
    prompt = f"""Write a brief, warm narrative summary of this person's day based on their conversation transcripts.
Write in second person ("You..."). Keep it to 3-5 sentences.

Transcripts:
{context}
"""
    try:
        narrative = await _call_openai_json_raw(prompt, config.PROACTIVE_MODEL)
        return {"narrative": narrative}
    except Exception:
        return {"narrative": None}


@app.get("/api/chunks/{chunk_id}/audio-url")
async def get_audio_url(chunk_id: str):
    row = await db.fetch_one(
        "SELECT audio_file_path FROM memory_chunks WHERE id = $1", chunk_id
    )
    if not row or not row["audio_file_path"]:
        raise HTTPException(404, "Chunk or audio not found")

    gcs_path = row["audio_file_path"]
    if not gcs_path.startswith("gs://"):
        raise HTTPException(400, "Audio not in GCS")

    path = gcs_path.replace("gs://", "", 1)
    bucket_name, _, blob_name = path.partition("/")
    url = _generate_signed_url(blob_name, bucket_name=bucket_name)
    return {"audio_url": url}


@app.post("/api/speakers")
async def create_speaker(
    name: str,
    user_id: str = "default",
    is_primary: bool = False,
    color: int = 0,
):
    speaker_id = str(uuid.uuid4())
    now_ms = int(time.time() * 1000)
    await db.execute(
        """INSERT INTO speakers (id, user_id, name, is_primary, enrolled_at, color)
           VALUES ($1, $2, $3, $4, $5, $6)""",
        speaker_id, user_id, name, is_primary, now_ms, color,
    )
    return {"id": speaker_id, "name": name, "is_primary": is_primary, "enrolled_at": now_ms, "color": color}


@app.delete("/api/speakers/{speaker_id}")
async def delete_speaker(speaker_id: str):
    result = await db.execute("DELETE FROM speakers WHERE id = $1", speaker_id)
    if "DELETE 0" in str(result):
        raise HTTPException(404, "Speaker not found")
    return {"status": "deleted"}


class ExclusionZoneRequest(BaseModel):
    label: str
    latitude: float
    longitude: float
    radius_meters: float = 200.0
    user_id: str = "default"


@app.get("/api/exclusion-zones")
async def list_exclusion_zones(user_id: str = "default"):
    rows = await db.fetch_all(
        """SELECT id, label, latitude, longitude, radius_meters
           FROM known_places
           WHERE is_exclusion = true AND user_id = $1
           ORDER BY label ASC""",
        user_id,
    )
    return [dict(r) for r in rows]


@app.post("/api/exclusion-zones")
async def create_exclusion_zone(req: ExclusionZoneRequest):
    zone_id = str(uuid.uuid4())
    now_ms = int(time.time() * 1000)
    await db.execute(
        """INSERT INTO known_places
           (id, label, latitude, longitude, radius_meters, visit_count, last_visited_at,
            is_exclusion, user_id)
           VALUES ($1, $2, $3, $4, $5, 0, $6, true, $7)""",
        zone_id, req.label, req.latitude, req.longitude,
        req.radius_meters, now_ms, req.user_id,
    )
    return {
        "id": zone_id,
        "label": req.label,
        "latitude": req.latitude,
        "longitude": req.longitude,
        "radius_meters": req.radius_meters,
    }


@app.delete("/api/exclusion-zones/{zone_id}")
async def delete_exclusion_zone(zone_id: str):
    result = await db.execute(
        "DELETE FROM known_places WHERE id = $1 AND is_exclusion = true",
        zone_id,
    )
    if "DELETE 0" in str(result):
        raise HTTPException(404, "Exclusion zone not found")
    return {"status": "deleted"}


@app.post("/api/search")
async def search(req: SearchRequest):
    query_embedding = await _embed_text(req.query)
    if not query_embedding:
        return {"chunks": [], "utterances": [], "answer": None}

    embedding_str = _vector_literal(query_embedding)

    chunks = await db.fetch_all(
        """SELECT id, transcript, summary, start_timestamp, place_name,
                   1 - (embedding <=> $2::vector) AS similarity
            FROM memory_chunks
            WHERE embedding IS NOT NULL AND user_id = $3
            ORDER BY embedding <=> $2::vector
            LIMIT $1""",
        req.limit, embedding_str, req.user_id,
    )

    utterances = await db.fetch_all(
        """SELECT u.id, u.chunk_id, u.text, u.timestamp, u.speaker_id,
                   1 - (u.embedding <=> $2::vector) AS similarity
            FROM utterances u
            JOIN memory_chunks mc ON u.chunk_id = mc.id
            WHERE u.is_embedded = true AND mc.user_id = $3
            ORDER BY u.embedding <=> $2::vector
            LIMIT $1""",
        req.limit, embedding_str, req.user_id,
    )

    # Synthesize answer from top results
    answer = None
    context_parts = []
    for c in chunks[:5]:
        context_parts.append(c["transcript"] or c["summary"] or "")
    for u in utterances[:5]:
        context_parts.append(u["text"])
    context_text = "\n".join(p for p in context_parts if p)

    if context_text.strip():
        prompt = f"""Based on these conversation excerpts from the user's recordings, answer their question concisely.

Question: {req.query}

Excerpts:
{context_text}

If the excerpts don't contain enough information to answer, say so briefly.
"""
        try:
            answer_result = await _call_openai_json_raw(prompt, config.CLAIM_MODEL)
            answer = answer_result
        except Exception:
            pass

    return {
        "chunks": [dict(r) for r in chunks],
        "utterances": [dict(r) for r in utterances],
        "answer": answer,
    }


@app.post("/api/upload-url")
async def get_upload_url(filename: str):
    # Sanitize filename: strip path components, keep only the basename
    safe_name = os.path.basename(filename)
    if not safe_name or safe_name.startswith("."):
        safe_name = f"{uuid.uuid4()}.m4a"
    url = _generate_signed_url(f"audio/{safe_name}", method="PUT", content_type="audio/mp4")
    return {"upload_url": url, "gcs_path": f"gs://{config.GCS_BUCKET}/audio/{safe_name}"}


# ── Processing handlers (called by Workflows) ───────────────────────────────

@app.post("/process/transcribe")
async def process_transcribe(req: ProcessRequest):
    chunk = await db.fetch_one(
        "SELECT * FROM memory_chunks WHERE id = $1", req.chunk_id
    )
    if not chunk:
        raise HTTPException(404, "Chunk not found")

    audio_url = _gcs_to_url(chunk["audio_file_path"])

    # Call Deepgram Nova-3 with diarization
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.post(
            "https://api.deepgram.com/v1/listen"
            "?model=nova-3&diarize=true&diarize_version=latest"
            "&utterances=true&smart_format=true&punctuate=true&language=en-US",
            headers={
                "Authorization": f"Token {config.DEEPGRAM_API_KEY}",
                "Content-Type": "application/json",
            },
            json={"url": audio_url},
        )
        resp.raise_for_status()
        result = resp.json()

    # Parse utterances
    utterances = _parse_deepgram_utterances(result)
    transcript = " ".join(u["text"] for u in utterances)

    log.info(
        f"Chunk {req.chunk_id}: {len(transcript)} chars, "
        f"{len(utterances)} utterances, "
        f"{len(set(u['speaker'] for u in utterances))} speakers"
    )

    # Generate summary
    sentences = [s.strip() for s in transcript.split(".") if s.strip()]
    summary = ". ".join(sentences[:3]) + "." if sentences else ""

    # Store transcript + summary
    await db.execute(
        """UPDATE memory_chunks
           SET transcript = $1, summary = $2, status = 'TRANSCRIBED'
           WHERE id = $3""",
        transcript, summary[:500], req.chunk_id,
    )

    # Store utterances
    for utt in utterances:
        ts = chunk["start_timestamp"] + int(utt["start"] * 1000)
        end_ts = chunk["start_timestamp"] + int(utt["end"] * 1000)
        await db.execute(
            """INSERT INTO utterances (id, chunk_id, timestamp, end_timestamp, text, diarization_label)
               VALUES ($1, $2, $3, $4, $5, $6)""",
            str(uuid.uuid4()), req.chunk_id, ts, end_ts,
            utt["text"], utt["speaker"],
        )

    return {
        "chunk_id": req.chunk_id,
        "transcript_length": len(transcript),
        "utterance_count": len(utterances),
        "speaker_count": len(set(u["speaker"] for u in utterances)),
    }


@app.post("/process/embed-chunk")
async def process_embed_chunk(req: ProcessRequest):
    chunk = await db.fetch_one(
        "SELECT transcript, summary FROM memory_chunks WHERE id = $1",
        req.chunk_id,
    )
    if not chunk or not chunk["transcript"]:
        return {"chunk_id": req.chunk_id, "embedded": False}

    text = f"{chunk['summary'] or ''} {chunk['transcript']}"
    embedding = await _embed_text(text)
    if not embedding:
        return {"chunk_id": req.chunk_id, "embedded": False}

    embedding_str = _vector_literal(embedding)
    await db.execute(
        """UPDATE memory_chunks
            SET embedding = $2::vector, status = 'EMBEDDED'
            WHERE id = $1""",
        req.chunk_id, embedding_str,
    )
    return {"chunk_id": req.chunk_id, "embedded": True}


@app.post("/process/extract-claims")
async def process_extract_claims(req: ProcessRequest):
    chunk = await db.fetch_one(
        """SELECT id, transcript, start_timestamp, place_name
           FROM memory_chunks WHERE id = $1""",
        req.chunk_id,
    )
    if not chunk or not chunk["transcript"]:
        return {"chunk_id": req.chunk_id, "claims": 0}

    prompt = f"""Extract factual claims, opinions, preferences, and commitments from this conversation transcript.

For each claim, return JSON with:
- topic: short topic label
- claim_text: the claim in a clear sentence
- raw_quote: the exact quote from the transcript
- type: one of "factual", "opinion", "preference", "location_claim", "time_claim", "commitment"
- speaker_name: if identifiable, otherwise null

Transcript:
{chunk['transcript']}

Return a JSON array of claims. If no claims found, return [].
"""

    claims = await _call_openai_json(prompt, config.CLAIM_MODEL)
    if not isinstance(claims, list):
        claims = []

    for claim in claims:
        claim_embedding = await _embed_text(f"{claim.get('topic', '')} {claim.get('claim_text', '')}")
        embedding_str = _vector_literal(claim_embedding) if claim_embedding else None

        if embedding_str:
            await db.execute(
                """INSERT INTO claims
                    (id, chunk_id, speaker_name, timestamp, topic, claim_text,
                     raw_quote, type, place_name, created_at, embedding)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11::vector)""",
                str(uuid.uuid4()), req.chunk_id,
                claim.get("speaker_name"),
                chunk["start_timestamp"],
                claim.get("topic", "unknown"),
                claim.get("claim_text", ""),
                claim.get("raw_quote", ""),
                claim.get("type", "factual"),
                chunk["place_name"],
                chunk["start_timestamp"],
                embedding_str,
            )
        else:
            await db.execute(
                """INSERT INTO claims
                    (id, chunk_id, speaker_name, timestamp, topic, claim_text,
                     raw_quote, type, place_name, created_at)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)""",
                str(uuid.uuid4()), req.chunk_id,
                claim.get("speaker_name"),
                chunk["start_timestamp"],
                claim.get("topic", "unknown"),
                claim.get("claim_text", ""),
                claim.get("raw_quote", ""),
                claim.get("type", "factual"),
                chunk["place_name"],
                chunk["start_timestamp"],
            )

    log.info(f"Chunk {req.chunk_id}: extracted {len(claims)} claims")
    return {"chunk_id": req.chunk_id, "claims": len(claims)}


@app.post("/process/detect-commitments")
async def process_detect_commitments(req: ProcessRequest):
    chunk = await db.fetch_one(
        "SELECT transcript, commitments FROM memory_chunks WHERE id = $1",
        req.chunk_id,
    )
    if not chunk or not chunk["transcript"]:
        return {"chunk_id": req.chunk_id, "commitments": 0}

    prompt = f"""Identify any commitments, promises, or action items in this conversation.

For each, return JSON with:
- text: what was committed
- type: "promise", "action_item", "deadline", or "plan"
- who: who made the commitment (if identifiable)

Transcript:
{chunk['transcript']}

Return a JSON array. If none found, return [].
"""

    commitments = await _call_openai_json(prompt, config.COMMITMENT_MODEL)
    if not isinstance(commitments, list):
        commitments = []

    if commitments:
        await db.execute(
            "UPDATE memory_chunks SET commitments = $1 WHERE id = $2",
            json.dumps(commitments), req.chunk_id,
        )

    log.info(f"Chunk {req.chunk_id}: {len(commitments)} commitments")
    return {"chunk_id": req.chunk_id, "commitments": len(commitments)}


@app.post("/process/check-consistency")
async def process_check_consistency(req: ProcessRequest):
    new_claims = await db.fetch_all(
        "SELECT * FROM claims WHERE chunk_id = $1", req.chunk_id
    )
    if not new_claims:
        return {"chunk_id": req.chunk_id, "inconsistencies": 0}

    # Find potentially related older claims by topic
    topics = list(set(c["topic"] for c in new_claims))
    placeholders = ", ".join(f"${i+2}" for i in range(len(topics)))
    old_claims = await db.fetch_all(
        f"""SELECT * FROM claims
            WHERE chunk_id != $1 AND topic IN ({placeholders})
            ORDER BY timestamp DESC LIMIT 50""",
        req.chunk_id, *topics,
    )

    if not old_claims:
        return {"chunk_id": req.chunk_id, "inconsistencies": 0}

    new_summary = "\n".join(
        f"[{c['type']}] {c['topic']}: {c['claim_text']}" for c in new_claims
    )
    old_summary = "\n".join(
        f"[{c['type']}] {c['topic']}: {c['claim_text']}" for c in old_claims
    )

    prompt = f"""Compare these NEW claims against PREVIOUS claims and identify any contradictions or inconsistencies.

NEW claims:
{new_summary}

PREVIOUS claims:
{old_summary}

For each inconsistency, return JSON with:
- title: short title
- body: explanation of the contradiction
- severity: "low", "medium", or "high"

Return a JSON array. If no inconsistencies found, return [].
"""

    inconsistencies = await _call_openai_json(prompt, config.CLAIM_MODEL)
    if not isinstance(inconsistencies, list):
        inconsistencies = []

    now_ms = int(time.time() * 1000)
    for inc in inconsistencies:
        await db.execute(
            """INSERT INTO insights (id, type, title, body, source_timestamp, created_at)
               VALUES ($1, 'inconsistency', $2, $3, $4, $5)""",
            str(uuid.uuid4()),
            inc.get("title", "Inconsistency"),
            inc.get("body", ""),
            new_claims[0]["timestamp"],
            now_ms,
        )

    log.info(f"Chunk {req.chunk_id}: {len(inconsistencies)} inconsistencies")
    return {"chunk_id": req.chunk_id, "inconsistencies": len(inconsistencies)}


@app.post("/process/embed-utterances")
async def process_embed_utterances(req: ProcessRequest):
    rows = await db.fetch_all(
        """SELECT id, text FROM utterances
           WHERE chunk_id = $1 AND is_embedded = false AND text != ''""",
        req.chunk_id,
    )
    if not rows:
        return {"chunk_id": req.chunk_id, "embedded": 0}

    texts = [r["text"] for r in rows]
    embeddings = await _embed_batch(texts)

    count = 0
    for row, emb in zip(rows, embeddings):
        if emb:
            embedding_str = _vector_literal(emb)
            await db.execute(
                """UPDATE utterances
                    SET embedding = $2::vector, is_embedded = true
                    WHERE id = $1""",
                row["id"], embedding_str,
            )
            count += 1

    log.info(f"Chunk {req.chunk_id}: embedded {count} utterances")
    return {"chunk_id": req.chunk_id, "embedded": count}


@app.post("/process/proactive")
async def process_proactive(req: ProcessRequest):
    # Get user_id from the chunk to scope queries
    chunk = await db.fetch_one(
        "SELECT user_id FROM memory_chunks WHERE id = $1", req.chunk_id
    )
    user_id = chunk["user_id"] if chunk else "default"

    recent_chunks = await db.fetch_all(
        """SELECT id, transcript, summary, place_name, start_timestamp
           FROM memory_chunks
           WHERE status = 'EMBEDDED' AND transcript IS NOT NULL AND user_id = $1
           ORDER BY start_timestamp DESC LIMIT 10""",
        user_id,
    )
    if len(recent_chunks) < 2:
        return {"chunk_id": req.chunk_id, "insights": 0}

    chunk_ids = [c["id"] for c in recent_chunks]
    placeholders = ", ".join(f"${i+1}" for i in range(len(chunk_ids)))
    recent_claims = await db.fetch_all(
        f"""SELECT topic, claim_text, type, speaker_name, place_name
           FROM claims WHERE chunk_id IN ({placeholders})
           ORDER BY timestamp DESC LIMIT 30""",
        *chunk_ids,
    )

    context = "\n\n".join(
        f"[{r['place_name'] or 'Unknown'}] {r['summary'] or r['transcript'][:200]}"
        for r in recent_chunks
    )
    claims_text = "\n".join(
        f"- [{c['type']}] {c['topic']}: {c['claim_text']}" for c in recent_claims
    )

    prompt = f"""You are a personal memory assistant analyzing recent conversations.

Recent conversation summaries:
{context}

Recent claims and facts:
{claims_text}

Generate useful proactive insights such as:
- Follow-up reminders for commitments
- Patterns you notice across conversations
- Things the user might want to remember

For each insight return JSON with:
- type: "reminder", "pattern", "follow_up"
- title: short title
- body: the insight

Return a JSON array. If nothing noteworthy, return [].
"""

    insights = await _call_openai_json(prompt, config.PROACTIVE_MODEL)
    if not isinstance(insights, list):
        insights = []

    now_ms = int(time.time() * 1000)
    for ins in insights:
        await db.execute(
            """INSERT INTO insights (id, type, title, body, source_timestamp, created_at)
               VALUES ($1, $2, $3, $4, $5, $6)""",
            str(uuid.uuid4()),
            ins.get("type", "pattern"),
            ins.get("title", ""),
            ins.get("body", ""),
            recent_chunks[0]["start_timestamp"],
            now_ms,
        )

    log.info(f"Proactive analysis: {len(insights)} insights")
    return {"chunk_id": req.chunk_id, "insights": len(insights)}


@app.get("/health")
async def health():
    try:
        await db.fetch_one("SELECT 1")
        return {"status": "healthy", "db": "connected"}
    except Exception:
        return {"status": "unhealthy", "db": "connection failed"}


# ── Workflow trigger ─────────────────────────────────────────────────────────

async def trigger_workflow(chunk_id: str):
    """Trigger the GCP Workflow for chunk processing."""
    if not config.GCP_PROJECT:
        log.warning("GCP_PROJECT not set, skipping workflow trigger")
        return

    from google.cloud.workflows import executions_v1

    client = executions_v1.ExecutionsAsyncClient()
    parent = (
        f"projects/{config.GCP_PROJECT}"
        f"/locations/{config.GCP_REGION}"
        f"/workflows/{config.WORKFLOW_NAME}"
    )
    execution = executions_v1.Execution(
        argument=json.dumps({"chunk_id": chunk_id})
    )
    await client.create_execution(parent=parent, execution=execution)
    log.info(f"Workflow triggered for chunk {chunk_id}")


# ── Helpers ──────────────────────────────────────────────────────────────────

def _parse_deepgram_utterances(result: dict) -> list[dict]:
    results = result.get("results", {})
    utterances = results.get("utterances")

    if utterances:
        return [
            {
                "text": u.get("transcript", ""),
                "start": u.get("start", 0),
                "end": u.get("end", 0),
                "speaker": u.get("speaker", 0),
            }
            for u in utterances
            if u.get("transcript")
        ]

    # Fallback: build from word-level diarization
    channels = results.get("channels", [])
    if not channels:
        return []
    alternatives = channels[0].get("alternatives", [])
    if not alternatives:
        return []
    words = alternatives[0].get("words", [])

    built = []
    current_speaker = -1
    current_words = []
    current_start = 0.0
    current_end = 0.0

    for w in words:
        speaker = w.get("speaker", 0)
        if speaker != current_speaker and current_words:
            built.append({
                "text": " ".join(current_words),
                "start": current_start,
                "end": current_end,
                "speaker": current_speaker,
            })
            current_words = []

        if not current_words:
            current_start = w.get("start", 0)
            current_speaker = speaker

        current_words.append(w.get("punctuated_word", w.get("word", "")))
        current_end = w.get("end", 0)

    if current_words:
        built.append({
            "text": " ".join(current_words),
            "start": current_start,
            "end": current_end,
            "speaker": current_speaker,
        })

    return built


async def _embed_text(text: str) -> list[float] | None:
    if not text.strip():
        return None
    result = await _embed_batch([text])
    return result[0] if result else None


async def _embed_batch(texts: list[str]) -> list[list[float] | None]:
    if not texts:
        return []
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            "https://api.openai.com/v1/embeddings",
            headers={"Authorization": f"Bearer {config.OPENAI_API_KEY}"},
            json={
                "model": config.EMBEDDING_MODEL,
                "input": texts,
            },
        )
        resp.raise_for_status()
        data = resp.json()["data"]
        # Sort by index to preserve order
        data.sort(key=lambda x: x["index"])
        return [d["embedding"] for d in data]


async def _call_openai_json(prompt: str, model: str) -> list | dict:
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            "https://api.openai.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {config.OPENAI_API_KEY}"},
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "response_format": {"type": "json_object"},
                "temperature": 0.2,
            },
        )
        resp.raise_for_status()
        content = resp.json()["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        # Handle {"claims": [...]} or {"items": [...]} wrapper patterns
        if isinstance(parsed, dict):
            for key in parsed:
                if isinstance(parsed[key], list):
                    return parsed[key]
            return parsed
        return parsed


async def _call_openai_json_raw(prompt: str, model: str) -> str:
    """Call OpenAI and return the raw text response (not JSON-parsed)."""
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            "https://api.openai.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {config.OPENAI_API_KEY}"},
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.3,
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]


def _generate_signed_url(
    blob_path: str,
    method: str = "GET",
    content_type: str | None = None,
    expiration_minutes: int = 30,
    bucket_name: str | None = None,
) -> str:
    """Generate a V4 signed URL using IAM SignBlob (works on Cloud Run)."""
    credentials, project = google.auth.default()
    # Refresh credentials to get the service account email
    if hasattr(credentials, "refresh"):
        credentials.refresh(google.auth.transport.requests.Request())
    signing_credentials = None
    if hasattr(credentials, "service_account_email"):
        signer = google.auth.iam.Signer(
            google.auth.transport.requests.Request(),
            credentials,
            credentials.service_account_email,
        )
        signing_credentials = google.oauth2.service_account.Credentials(
            signer=signer,
            service_account_email=credentials.service_account_email,
            token_uri="https://oauth2.googleapis.com/token",
        )

    bkt = bucket_name or config.GCS_BUCKET
    client = gcs.Client()
    bucket = client.bucket(bkt)
    blob = bucket.blob(blob_path)
    kwargs = dict(
        version="v4",
        expiration=timedelta(minutes=expiration_minutes),
        method=method,
    )
    if content_type:
        kwargs["content_type"] = content_type
    if signing_credentials:
        kwargs["credentials"] = signing_credentials
    return blob.generate_signed_url(**kwargs)


def _gcs_to_url(gcs_path: str) -> str:
    """Convert gs://bucket/path to a signed URL for Deepgram."""
    if not gcs_path.startswith("gs://"):
        return gcs_path
    path = gcs_path.replace("gs://", "", 1)
    bucket_name, _, blob_name = path.partition("/")
    return _generate_signed_url(blob_name, bucket_name=bucket_name)


def _vector_literal(embedding: list[float]) -> str:
    """Format embedding as a pgvector literal string."""
    return "[" + ",".join(f"{v:.8f}" for v in embedding) + "]"
