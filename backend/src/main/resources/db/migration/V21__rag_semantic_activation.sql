-- Why this migration exists:
-- V17 is intentionally non-blocking when pgvector is absent. An operator may
-- install pgvector later, after V17 has already been recorded by Flyway. This
-- function gives that operator an explicit, idempotent activation step instead
-- of silently leaving PostgreSQL in lexical-only mode.

CREATE OR REPLACE FUNCTION rag.enable_semantic_retrieval()
RETURNS boolean
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RETURN false;
    END IF;

    EXECUTE 'ALTER TABLE rag.chunk ADD COLUMN IF NOT EXISTS embedding_model varchar(120)';
    EXECUTE 'ALTER TABLE rag.chunk ADD COLUMN IF NOT EXISTS embedding_version varchar(120)';
    EXECUTE 'ALTER TABLE rag.chunk ADD COLUMN IF NOT EXISTS embedding_dimensions integer';
    EXECUTE 'ALTER TABLE rag.chunk ADD COLUMN IF NOT EXISTS embedding vector(1536)';

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'rag_chunk_embedding_contract_ck'
          AND conrelid = 'rag.chunk'::regclass
    ) THEN
        EXECUTE '
            ALTER TABLE rag.chunk
                ADD CONSTRAINT rag_chunk_embedding_contract_ck
                CHECK (
                    (embedding IS NULL AND embedding_model IS NULL AND embedding_version IS NULL AND embedding_dimensions IS NULL)
                    OR
                    (embedding IS NOT NULL AND embedding_model IS NOT NULL AND embedding_version IS NOT NULL AND embedding_dimensions = 1536)
                )';
    END IF;

    EXECUTE '
        CREATE INDEX IF NOT EXISTS rag_chunk_embedding_hnsw_idx
        ON rag.chunk USING hnsw (embedding vector_cosine_ops)
        WHERE embedding IS NOT NULL AND chunk_status = ''CURRENT''';
    RETURN true;
END;
$$;
