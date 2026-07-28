from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        case_sensitive=True,
        env_file=".env",
        env_file_encoding="utf-8",
    )

    PROJECT_NAME: str = "AI Agent & Chatbot Server"
    VERSION: str = "2.0.0"
    API_V1_STR: str = "/api/v1"

    # CORS Configuration
    ALLOWED_ORIGINS: list[str] = [
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://localhost:8080",
        "http://127.0.0.1:8080",
    ]

    # Internal API authentication (Spring Boot → Python)
    INTERNAL_API_KEY: str = ""
    APP_ENV: str = "local"

    # AI Model Keys
    LLM_PROVIDER: str = "openai"
    OPENAI_API_KEY: str = ""
    OPENAI_BASE_URL: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o-mini"
    LLM_TIMEOUT_SECONDS: float = 12.0

    # Production RAG controls. The database URL is intentionally unset by
    # default: the deterministic agent remains available until an operator has
    # provisioned the dedicated rag schema and least-privilege role.
    RAG_ENABLED: bool = False
    RAG_DATABASE_URL: str = ""
    # The runtime RAG path deliberately uses PostgreSQL full-text search only;
    # no embedding provider is required to operate it.
    RAG_TOP_K: int = 8
    RAG_MAX_CHUNK_CHARS: int = 2400

    @property
    def is_production(self) -> bool:
        return self.APP_ENV.lower() in {"prod", "production"}

    def validate_runtime(self) -> None:
        if self.is_production and not self.INTERNAL_API_KEY:
            raise RuntimeError("INTERNAL_API_KEY must be configured in production.")
        if self.RAG_ENABLED and not self.RAG_DATABASE_URL:
            raise RuntimeError("RAG_DATABASE_URL must be configured when RAG_ENABLED=true.")


settings = Settings()
