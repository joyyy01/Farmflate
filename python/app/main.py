from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.outbound_http import outbound_http_client
from app.rag.retriever import rag_retriever


from app.api.v1.router import api_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings.validate_runtime()
    await outbound_http_client.start()
    try:
        yield
    finally:
        await rag_retriever.close()
        await outbound_http_client.close()

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    lifespan=lifespan,
)
# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Root & Health check
@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "Python AI Server",
        "version": settings.VERSION,
        "rag": "enabled" if settings.RAG_DATABASE_URL else "disabled",
    }

# Include API v1 Router
app.include_router(api_router, prefix=settings.API_V1_STR)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
