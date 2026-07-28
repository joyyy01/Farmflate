from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.api.v1.router import api_router

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=f"{settings.API_V1_STR}/openapi.json"
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
        "rag": "enabled" if settings.RAG_ENABLED else "disabled",
    }

# Include API v1 Router
app.include_router(api_router, prefix=settings.API_V1_STR)


@app.on_event("startup")
async def validate_runtime_configuration() -> None:
    settings.validate_runtime()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
