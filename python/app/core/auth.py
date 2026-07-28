from secrets import compare_digest

from fastapi import Header, HTTPException

from app.core.config import settings


async def verify_internal_key(
    x_farmflate_internal_key: str | None = Header(default=None),
) -> None:
    if not settings.INTERNAL_API_KEY:
        raise HTTPException(status_code=503, detail="Internal authentication is not configured.")
    if not x_farmflate_internal_key or not compare_digest(x_farmflate_internal_key, settings.INTERNAL_API_KEY):
        raise HTTPException(status_code=401, detail="Internal API key is missing or invalid.")
