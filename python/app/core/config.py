from pydantic_settings import BaseSettings
import os

class Settings(BaseSettings):
    PROJECT_NAME: str = "AI Agent & Chatbot Server"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # CORS Configuration
    ALLOWED_ORIGINS: list[str] = [
        "http://localhost:5173",  # React Dev Server
        "http://localhost:3000",
        "http://localhost:8080"   # Spring Boot Server
    ]
    
    # AI Model Keys (Set in .env)
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")

    class Config:
        case_sensitive = True

settings = Settings()
