-- A retrieval score is comparable only when the expected chunks came from the
-- same current corpus and the same label provenance.
ALTER TABLE rag.eval_run
    ADD COLUMN IF NOT EXISTS corpus_fingerprint char(64) NOT NULL DEFAULT repeat('0', 64),
    ADD COLUMN IF NOT EXISTS evaluation_origin varchar(32) NOT NULL DEFAULT 'LEGACY'
    CHECK (evaluation_origin IN ('LEGACY', 'AUTO_GENERATED', 'HUMAN_REVIEWED')),
    ADD COLUMN IF NOT EXISTS corpus_document_count integer NOT NULL DEFAULT 0 CHECK (corpus_document_count >= 0),
    ADD COLUMN IF NOT EXISTS corpus_chunk_count integer NOT NULL DEFAULT 0 CHECK (corpus_chunk_count >= 0);

ALTER TABLE rag.eval_result
    ADD COLUMN IF NOT EXISTS reciprocal_rank numeric(8, 5),
    ADD COLUMN IF NOT EXISTS first_relevant_rank integer CHECK (first_relevant_rank > 0);
