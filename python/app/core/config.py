from pydantic_settings import BaseSettings
import os

class Settings(BaseSettings):
    PROJECT_NAME: str = "AI Agent & Chatbot Server"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # CORS Configuration
    ALLOWED_ORIGINS: list[str] = [
        "http://localhost:5173",  # React Dev Server
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://localhost:8080",   # Spring Boot Server
        "http://127.0.0.1:8080"
    ]
    
    # AI Model Keys (Strictly loaded from .env)
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
    LLM_PROVIDER: str = os.getenv("LLM_PROVIDER", "gemini")
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    OPENAI_BASE_URL: str = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    OPENAI_MODEL: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    LLM_TIMEOUT_SECONDS: float = float(os.getenv("LLM_TIMEOUT_SECONDS", "12"))

    class Config:
        case_sensitive = True

settings = Settings()
