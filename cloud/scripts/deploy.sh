#!/usr/bin/env bash
set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
# Set these or pass as environment variables

PROJECT_ID="${GCP_PROJECT:?Set GCP_PROJECT}"
REGION="${GCP_REGION:-us-east1}"
SERVICE_NAME="memorystream-api"
WORKFLOW_NAME="process-chunk"
BUCKET_NAME="${GCS_BUCKET:-${PROJECT_ID}-memorystream-audio}"
DB_INSTANCE="memorystream-db"
DB_NAME="memorystream"
DB_USER="memorystream"
DB_PASSWORD="${DB_PASSWORD:?Set DB_PASSWORD}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLOUD_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== MemoryStream Cloud Deploy ==="
echo "Project: $PROJECT_ID"
echo "Region:  $REGION"
echo ""

# ── Enable APIs ──────────────────────────────────────────────────────────────

echo "--- Enabling GCP APIs ---"
gcloud services enable \
  sqladmin.googleapis.com \
  run.googleapis.com \
  workflows.googleapis.com \
  storage.googleapis.com \
  cloudbuild.googleapis.com \
  --project="$PROJECT_ID" --quiet

# ── Cloud Storage ────────────────────────────────────────────────────────────

echo "--- Creating Cloud Storage bucket ---"
gsutil ls "gs://$BUCKET_NAME" 2>/dev/null || \
  gsutil mb -l "$REGION" -p "$PROJECT_ID" "gs://$BUCKET_NAME"

# Clear any existing CORS -- uploads come from Android, not browsers
echo '[]' > /tmp/cors-empty.json
gsutil cors set /tmp/cors-empty.json "gs://$BUCKET_NAME"

# ── Cloud SQL ────────────────────────────────────────────────────────────────

echo "--- Creating Cloud SQL instance ---"
if ! gcloud sql instances describe "$DB_INSTANCE" --project="$PROJECT_ID" &>/dev/null; then
  gcloud sql instances create "$DB_INSTANCE" \
    --database-version=POSTGRES_15 \
    --tier=db-f1-micro \
    --region="$REGION" \
    --project="$PROJECT_ID" \
    --storage-auto-increase \
    --database-flags=cloudsql.enable_pgvector=on \
    --quiet
  echo "Waiting for instance to be ready..."
  gcloud sql instances describe "$DB_INSTANCE" --project="$PROJECT_ID" --format='value(state)'
fi

# Create database and user
gcloud sql databases create "$DB_NAME" \
  --instance="$DB_INSTANCE" --project="$PROJECT_ID" --quiet 2>/dev/null || true

gcloud sql users create "$DB_USER" \
  --instance="$DB_INSTANCE" --password="$DB_PASSWORD" \
  --project="$PROJECT_ID" --quiet 2>/dev/null || true

# Get connection name for Cloud Run
CONNECTION_NAME=$(gcloud sql instances describe "$DB_INSTANCE" \
  --project="$PROJECT_ID" --format='value(connectionName)')
echo "Cloud SQL connection: $CONNECTION_NAME"

# Initialize schema
echo "--- Initializing database schema ---"
echo "Run this manually via Cloud SQL proxy or console:"
echo "  gcloud sql connect $DB_INSTANCE --user=$DB_USER --database=$DB_NAME"
echo "  Then paste contents of: $CLOUD_DIR/schema/init.sql"
echo ""

# ── Cloud Run ────────────────────────────────────────────────────────────────

echo "--- Building and deploying Cloud Run service ---"
DATABASE_URL="postgresql+asyncpg://${DB_USER}:${DB_PASSWORD}@/${DB_NAME}?host=/cloudsql/${CONNECTION_NAME}"

gcloud run deploy "$SERVICE_NAME" \
  --source="$CLOUD_DIR/service" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --platform=managed \
  --allow-unauthenticated \
  --add-cloudsql-instances="$CONNECTION_NAME" \
  --set-env-vars="DATABASE_URL=$DATABASE_URL,GCP_PROJECT=$PROJECT_ID,GCS_BUCKET=$BUCKET_NAME,GCP_REGION=$REGION,DEEPGRAM_API_KEY=${DEEPGRAM_API_KEY:-},OPENAI_API_KEY=${OPENAI_API_KEY:-},WORKFLOW_NAME=$WORKFLOW_NAME" \
  --memory=512Mi \
  --cpu=1 \
  --timeout=300 \
  --min-instances=0 \
  --max-instances=5 \
  --quiet

CLOUD_RUN_URL=$(gcloud run services describe "$SERVICE_NAME" \
  --region="$REGION" --project="$PROJECT_ID" \
  --format='value(status.url)')
echo "Cloud Run URL: $CLOUD_RUN_URL"

# ── Workflows ────────────────────────────────────────────────────────────────

echo "--- Deploying GCP Workflow ---"
gcloud workflows deploy "$WORKFLOW_NAME" \
  --source="$CLOUD_DIR/workflows/process-chunk.yaml" \
  --location="$REGION" \
  --project="$PROJECT_ID" \
  --set-env-vars="CLOUD_RUN_URL=$CLOUD_RUN_URL" \
  --quiet

# ── Done ─────────────────────────────────────────────────────────────────────

echo ""
echo "=== Deploy Complete ==="
echo ""
echo "Cloud Run:  $CLOUD_RUN_URL"
echo "Bucket:     gs://$BUCKET_NAME"
echo "Database:   $CONNECTION_NAME"
echo "Workflow:   $WORKFLOW_NAME"
echo ""
echo "Next steps:"
echo "  1. Initialize the DB schema (see above)"
echo "  2. Set DEEPGRAM_API_KEY and OPENAI_API_KEY if not set"
echo "  3. Test: curl $CLOUD_RUN_URL/health"
echo "  4. Update Android app with CLOUD_RUN_URL"
