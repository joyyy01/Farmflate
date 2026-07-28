from urllib.parse import urlparse

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        case_sensitive=True,
        env_file=".env",
        env_file_encoding="utf-8",
        # The repository shares one .env file between Spring and Python.
        # Python must validate its own settings without rejecting unrelated
        # backend-only variables from that file.
        extra="ignore",
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

    # AI Model Keys
    LLM_PROVIDER: str = "openai"
    OPENAI_API_KEY: str = ""
    OPENAI_BASE_URL: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o-mini"
    LLM_TIMEOUT_SECONDS: float = 12.0

    # PostgreSQL is the RAG system of record and retrieval engine.
    RAG_DATABASE_URL: str = ""
    RAG_TOP_K: int = 8
    RAG_MAX_CHUNK_CHARS: int = 2400
    # A request may execute this many read-only tool steps, then receive one
    # final model turn to produce a cited answer.
    AGENT_MAX_TOOL_CALLS: int = 2
    AGENT_TOOL_TIMEOUT_SECONDS: float = 10.0

    def validate_runtime(self) -> None:
        if not self.INTERNAL_API_KEY or self.INTERNAL_API_KEY != self.INTERNAL_API_KEY.strip():
            raise RuntimeError("INTERNAL_API_KEY must be configured as a non-blank value.")
        rag_url = urlparse(self.RAG_DATABASE_URL)
        if rag_url.scheme not in {"postgres", "postgresql"} or not rag_url.netloc:
            raise RuntimeError("RAG_DATABASE_URL must be a PostgreSQL connection URL.")
        if not 1 <= self.RAG_TOP_K <= 10:
            raise RuntimeError("RAG_TOP_K must be between 1 and 10.")
        if not 200 <= self.RAG_MAX_CHUNK_CHARS <= 10_000:
            raise RuntimeError("RAG_MAX_CHUNK_CHARS must be between 200 and 10000.")
        if self.LLM_TIMEOUT_SECONDS <= 0:
            raise RuntimeError("LLM_TIMEOUT_SECONDS must be positive.")
        if self.AGENT_MAX_TOOL_CALLS != 2:
            raise RuntimeError("AGENT_MAX_TOOL_CALLS must be exactly 2 for the bounded agent workflow.")
        if self.AGENT_TOOL_TIMEOUT_SECONDS <= 0:
            raise RuntimeError("AGENT_TOOL_TIMEOUT_SECONDS must be positive.")


settings = Settings()
