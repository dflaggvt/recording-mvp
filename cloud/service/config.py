import os


# Database
DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql+asyncpg://memorystream:memorystream@localhost:5432/memorystream"
)
# asyncpg needs the raw DSN without the +asyncpg prefix
DATABASE_DSN = DATABASE_URL.replace("postgresql+asyncpg://", "postgresql://")

# GCP
GCP_PROJECT = os.environ.get("GCP_PROJECT", "")
GCS_BUCKET = os.environ.get("GCS_BUCKET", "memorystream-audio")
GCP_REGION = os.environ.get("GCP_REGION", "us-east1")
WORKFLOW_NAME = os.environ.get("WORKFLOW_NAME", "process-chunk")

# API Keys
DEEPGRAM_API_KEY = os.environ.get("DEEPGRAM_API_KEY", "")
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")

# Processing
EMBEDDING_MODEL = "text-embedding-3-small"
EMBEDDING_DIMENSIONS = 1536
CLAIM_MODEL = "gpt-4o"
COMMITMENT_MODEL = "gpt-4o-mini"
PROACTIVE_MODEL = "gpt-4o"
