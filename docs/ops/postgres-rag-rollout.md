# PostgreSQL RAG rollout

## Scope

RAG uses PostgreSQL as its only retrieval dependency. Approved documents are
stored in the `rag` schema and queried with native `tsvector` full-text search.
There is no embedding service, vector index, or request-time web crawl.

## Deployment

Deploy the normal backend Flyway migration. V16 creates the `rag` schema,
provenance tables, and the GIN index used by full-text search. It is a normal
transactional migration and needs no privileged extension installation.

```sql
SELECT table_name FROM information_schema.tables
 WHERE table_schema = 'rag' ORDER BY table_name;
SELECT indexname FROM pg_indexes
 WHERE schemaname = 'rag' AND tablename = 'chunk' ORDER BY indexname;
```

## Operation

Only an operator may ingest a document from the approved-source manifest. The
retrieval service reads sources whose approval status and expiry are valid, and
returns their canonical URL with the matching chunk. Revoke a source by setting
its approval status to `REVOKED`; do not delete provenance during an incident.
