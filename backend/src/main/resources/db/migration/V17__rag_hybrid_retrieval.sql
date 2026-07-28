-- Keep V16 immutable. Runtime retrieval is PostgreSQL full-text search, so a
-- PostgreSQL deployment without pgvector must still finish the RAG migration.
-- The semantic lane is created only when an operator has installed pgvector.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        ALTER TABLE rag.chunk
            ADD COLUMN embedding_model varchar(120),
            ADD COLUMN embedding_version varchar(120),
            ADD COLUMN embedding_dimensions integer,
            ADD COLUMN embedding vector(1536);

        ALTER TABLE rag.chunk
            ADD CONSTRAINT rag_chunk_embedding_contract_ck
            CHECK (
                (embedding IS NULL AND embedding_model IS NULL AND embedding_version IS NULL AND embedding_dimensions IS NULL)
                OR
                (embedding IS NOT NULL AND embedding_model IS NOT NULL AND embedding_version IS NOT NULL AND embedding_dimensions = 1536)
            );

        CREATE INDEX rag_chunk_embedding_hnsw_idx
            ON rag.chunk USING hnsw (embedding vector_cosine_ops)
            WHERE embedding IS NOT NULL AND chunk_status = 'CURRENT';
    END IF;
END
$$;
