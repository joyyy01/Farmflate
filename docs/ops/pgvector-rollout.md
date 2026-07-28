# pgvector RAG rollout

## Ownership and access boundary

PostgreSQL is the system of record for RAG knowledge. The application reads
approved, current, non-expired records only. Ingestion is operator-only: use a
separate ingestion credential and an approved source manifest; do not expose
write access to the user-facing application or read-only retrieval credential.

`rag.source.approval_status`, `rag.source.expires_at`, document/chunk status,
language, and expiry are the retrieval policy inputs. An approved source that
has expired is not eligible for retrieval.

## Privileged pgvector preflight

Before deploying Flyway V16, an authorized database operator must confirm that
the target PostgreSQL version is supported by the installed pgvector package
and that the Flyway deployment role is permitted to create extensions. This is
a privileged infrastructure preflight, not an application-runtime action:

```sql
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
CREATE EXTENSION IF NOT EXISTS vector;
```

Record the extension version and the authorized operator in the deployment
change record. If extension installation is centrally managed, the operator
performs the `CREATE EXTENSION` statement before Flyway. V16 deliberately keeps
the same idempotent statement so a Flyway execution is explicit about its
pgvector dependency; it must run under the privileged deployment role, never
the application runtime role.

## Flyway application-schema migration

With the preflight complete, execute the normal backend Flyway deployment. V16
creates only the dedicated `rag` schema, its tables, and normal transactional
indexes. It contains no `CREATE INDEX CONCURRENTLY`, so it is safe for Flyway's
transactional migration execution.

After migration, verify the schema contract:

```sql
SELECT table_name FROM information_schema.tables
 WHERE table_schema = 'rag' ORDER BY table_name;
SELECT indexname, indexdef FROM pg_indexes
 WHERE schemaname = 'rag' AND tablename = 'chunk' ORDER BY indexname;
```

## Large-production HNSW index procedure

For a large existing `rag.chunk`, build or rebuild the cosine index outside
Flyway and outside a transaction, after an operator-approved maintenance plan:

```sql
DROP INDEX CONCURRENTLY IF EXISTS rag.rag_chunk_embedding_hnsw;
CREATE INDEX CONCURRENTLY rag_chunk_embedding_hnsw
    ON rag.chunk USING hnsw (embedding vector_cosine_ops);
```

Do not run either statement inside `BEGIN`/`COMMIT`. Keep the existing index
until the replacement build is confirmed in `pg_indexes`; use exact-search
sampling to measure HNSW recall before accepting the rollout.

## Rollback

The schema migration is forward-only: do not drop RAG knowledge or the vector
extension as an incident rollback. To stop retrieval, revoke the application
read role or disable the affected source by changing its approval status to
`REVOKED` through the authorized operator workflow. To remove a faulty HNSW
index without blocking traffic, run:

```sql
DROP INDEX CONCURRENTLY IF EXISTS rag.rag_chunk_embedding_hnsw;
```

Retain source, document, chunk, ingestion, and evaluation provenance for
investigation; data deletion follows the separately approved retention policy.
