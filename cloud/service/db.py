import asyncpg
from contextlib import asynccontextmanager
from config import DATABASE_DSN

_pool: asyncpg.Pool | None = None


async def init_pool():
    global _pool
    _pool = await asyncpg.create_pool(DATABASE_DSN, min_size=2, max_size=10)


async def close_pool():
    global _pool
    if _pool:
        await _pool.close()
        _pool = None


@asynccontextmanager
async def get_conn():
    async with _pool.acquire() as conn:
        yield conn


async def fetch_one(query: str, *args):
    async with get_conn() as conn:
        return await conn.fetchrow(query, *args)


async def fetch_all(query: str, *args):
    async with get_conn() as conn:
        return await conn.fetch(query, *args)


async def execute(query: str, *args):
    async with get_conn() as conn:
        return await conn.execute(query, *args)
