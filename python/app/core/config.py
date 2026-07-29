from urllib.parse import quote, urlparse

from pydantic import AliasChoices, Field, model_validator
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
    INTERNAL_API_KEY: str = Field(
        default="",
        validation_alias=AliasChoices("INTERNAL_API_KEY", "PYTHON_INTERNAL_API_KEY"),
    )

    # AI Model Keys
    OPENAI_API_KEY: str = ""
    OPENAI_BASE_URL: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o-mini"
    # Bump when the Agent prompt, output schema, tool policy, or validation
    # contract changes. Metrics are filtered to this profile by default.
    AGENT_PIPELINE_VERSION: str = "sectioned-citations-v3"
    # Responses tool-calling may require an initial tool-selection turn and a
    # final cited-answer turn. Twelve seconds caused local ReadTimeout fallback
    # before a valid answer could be returned; keep a bounded 20-second default.
    LLM_TIMEOUT_SECONDS: float = 45.0

    # PostgreSQL is the RAG system of record and retrieval engine.
    RAG_DATABASE_URL: str = ""
    NONGSARO_API_KEY: str = ""
    DB_HOST: str = ""
    DB_PORT: int = 5432
    DB_NAME: str = ""
    DB_USER: str = ""
    DB_PASS: str = ""
    RAG_TOP_K: int = 8
    RAG_MAX_CHUNK_CHARS: int = 2400
    # Hybrid retrieval is an opt-in, not a default. Enable it only after the
    # project's evaluated retrieval set shows a measurable benefit over FTS.
    RAG_HYBRID_ENABLED: bool = False
    RAG_EMBEDDING_MODEL: str = "text-embedding-3-small"
    RAG_EMBEDDING_DIMENSIONS: int = 1536
    RAG_LEXICAL_CANDIDATE_LIMIT: int = 24
    RAG_SEMANTIC_CANDIDATE_LIMIT: int = 24
    RAG_RRF_K: int = 60
    RAG_SEMANTIC_AVAILABILITY_TTL_SECONDS: int = 60
    RAG_EVALUATION_MIN_CASES: int = 30
    RAG_HYBRID_MIN_RECALL_DELTA: float = 0.05
    RAG_HYBRID_MAX_CITATION_PRECISION_DROP: float = 0.0
    RAG_HYBRID_MAX_P95_LATENCY_RATIO: float = 1.5
    # A request may execute this many read-only tool steps, then receive one
    # final model turn to produce a cited answer.
    AGENT_MAX_TOOL_CALLS: int = 2
    AGENT_TOOL_TIMEOUT_SECONDS: float = 10.0
    AGENT_MAX_OUTPUT_TOKENS: int = 800

    # External MCP is a separate, opt-in read-only process. These values are
    # ignored by the normal Agent API unless MCP_ENABLED is true.
    MCP_ENABLED: bool = False
    MCP_ACCESS_TOKEN: str = ""
    MCP_HOST: str = "127.0.0.1"
    MCP_PORT: int = 8001
    MCP_SPRING_BASE_URL: str = "http://127.0.0.1:8080"
    MCP_SPRING_INTERNAL_TOKEN: str = ""

    @model_validator(mode="after")
    def derive_rag_database_url(self) -> "Settings":
        if self.RAG_DATABASE_URL:
            return self
        if not all((self.DB_HOST, self.DB_NAME, self.DB_USER)):
            return self

        username = quote(self.DB_USER, safe="")
        authority = f"{username}@{self.DB_HOST}:{self.DB_PORT}"
        if self.DB_PASS:
            authority = f"{username}:{quote(self.DB_PASS, safe='')}@{self.DB_HOST}:{self.DB_PORT}"
        self.RAG_DATABASE_URL = f"postgresql://{authority}/{self.DB_NAME}"
        return self

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
        if self.RAG_EMBEDDING_DIMENSIONS != 1536:
            raise RuntimeError("RAG_EMBEDDING_DIMENSIONS must match the PostgreSQL vector index dimension (1536).")
        if not 1 <= self.RAG_LEXICAL_CANDIDATE_LIMIT <= 100:
            raise RuntimeError("RAG_LEXICAL_CANDIDATE_LIMIT must be between 1 and 100.")
        if not 1 <= self.RAG_SEMANTIC_CANDIDATE_LIMIT <= 100:
            raise RuntimeError("RAG_SEMANTIC_CANDIDATE_LIMIT must be between 1 and 100.")
        if not 1 <= self.RAG_RRF_K <= 200:
            raise RuntimeError("RAG_RRF_K must be between 1 and 200.")
        if not 1 <= self.RAG_SEMANTIC_AVAILABILITY_TTL_SECONDS <= 600:
            raise RuntimeError("RAG_SEMANTIC_AVAILABILITY_TTL_SECONDS must be between 1 and 600.")
        if not 30 <= self.RAG_EVALUATION_MIN_CASES <= 10_000:
            raise RuntimeError("RAG_EVALUATION_MIN_CASES must be between 30 and 10000.")
        if not 0 <= self.RAG_HYBRID_MIN_RECALL_DELTA <= 1:
            raise RuntimeError("RAG_HYBRID_MIN_RECALL_DELTA must be between 0 and 1.")
        if not 0 <= self.RAG_HYBRID_MAX_CITATION_PRECISION_DROP <= 1:
            raise RuntimeError("RAG_HYBRID_MAX_CITATION_PRECISION_DROP must be between 0 and 1.")
        if not 1 <= self.RAG_HYBRID_MAX_P95_LATENCY_RATIO <= 10:
            raise RuntimeError("RAG_HYBRID_MAX_P95_LATENCY_RATIO must be between 1 and 10.")
        if not 1 <= self.LLM_TIMEOUT_SECONDS <= 60:
            raise RuntimeError("LLM_TIMEOUT_SECONDS must be between 1 and 60.")
        if not self.AGENT_PIPELINE_VERSION or self.AGENT_PIPELINE_VERSION != self.AGENT_PIPELINE_VERSION.strip() or len(self.AGENT_PIPELINE_VERSION) > 64:
            raise RuntimeError("AGENT_PIPELINE_VERSION must be a non-blank value up to 64 characters.")
        if self.AGENT_MAX_TOOL_CALLS != 2:
            raise RuntimeError("AGENT_MAX_TOOL_CALLS must be exactly 2 for the bounded agent workflow.")
        if self.AGENT_TOOL_TIMEOUT_SECONDS <= 0:
            raise RuntimeError("AGENT_TOOL_TIMEOUT_SECONDS must be positive.")
        if not 300 <= self.AGENT_MAX_OUTPUT_TOKENS <= 1200:
            raise RuntimeError("AGENT_MAX_OUTPUT_TOKENS must be between 300 and 1200.")
        if self.MCP_ENABLED:
            if not self.MCP_ACCESS_TOKEN or self.MCP_ACCESS_TOKEN != self.MCP_ACCESS_TOKEN.strip():
                raise RuntimeError("MCP_ACCESS_TOKEN must be a non-blank value when MCP_ENABLED is true.")
            if not self.MCP_SPRING_INTERNAL_TOKEN or self.MCP_SPRING_INTERNAL_TOKEN != self.MCP_SPRING_INTERNAL_TOKEN.strip():
                raise RuntimeError("MCP_SPRING_INTERNAL_TOKEN must be configured when MCP_ENABLED is true.")
            mcp_spring_url = urlparse(self.MCP_SPRING_BASE_URL)
            if mcp_spring_url.scheme not in {"http", "https"} or not mcp_spring_url.netloc:
                raise RuntimeError("MCP_SPRING_BASE_URL must be an HTTP URL when MCP_ENABLED is true.")
            if not 1 <= self.MCP_PORT <= 65535:
                raise RuntimeError("MCP_PORT must be between 1 and 65535.")


settings = Settings()
