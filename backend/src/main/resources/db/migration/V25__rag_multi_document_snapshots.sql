-- A source may publish many independent documents (for example, weekly bulletins).
-- A refresh supersedes only the same external publication, never the whole source.
UPDATE rag.document
SET external_id = source_version
WHERE external_id IS NULL OR btrim(external_id) = '';

ALTER TABLE rag.document
    ALTER COLUMN external_id SET NOT NULL;

CREATE UNIQUE INDEX rag_document_current_publication_idx
    ON rag.document (source_id, external_id)
    WHERE document_status = 'CURRENT';
