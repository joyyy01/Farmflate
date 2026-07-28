# Task 1 report: pgvector knowledge schema and rollout

## Implemented behavior

- Added Flyway V16 with the dedicated `rag` schema and the six RAG
  system-of-record tables: source, document, chunk, ingestion run, evaluation
  case, and evaluation result.
- Declared the explicit pgvector dependency, `vector(1536)` embeddings,
  cosine HNSW, full-text GIN, SHA-256 content provenance, source approval and
  expiry metadata, document/chunk lifecycle status, and ingestion actor status
  constrained to `OPERATOR`.
- Added ordinary filter indexes for source approval/expiry and document/chunk
  status/language/expiry to support approved, current, non-expired retrieval.
- Added an operations runbook that separates authorized extension preflight
  from the normal Flyway schema migration, documents operator-only ingestion,
  and supplies non-transactional concurrent HNSW reindex and rollback commands.

## Files changed

- `backend/src/main/resources/db/migration/V16__rag_knowledge_schema.sql`
- `backend/src/test/java/com/example/aiworkspace/migration/RagKnowledgeSchemaMigrationTest.java`
- `docs/ops/pgvector-rollout.md`
- `.superpowers/sdd/2026-07-28-production-rag-agent/task-1-report.md`

## TDD evidence

1. RED: after adding only `RagKnowledgeSchemaMigrationTest`, ran
   `bash gradlew test --tests '*RagKnowledgeSchemaMigrationTest' --no-daemon`
   in `backend`. It exited 1 after 34 seconds: the sole test failed with
   `java.nio.file.NoSuchFileException` at line 14 because V16 did not exist.
2. GREEN: after adding V16 and the runbook, the same focused command exited 0
   with `BUILD SUCCESSFUL in 13s`.
3. After the requested test-scope refinement, the focused command exited 0
   again with `BUILD SUCCESSFUL in 19s`.
4. Full backend suite: `bash gradlew test --no-daemon` exited 0 with
   `BUILD SUCCESSFUL in 20s`. The JVM emitted its existing bootstrap-classpath
   sharing warning but no test failure or compilation warning.

## Test judgment and self-review

The migration test deliberately remains one focused production-risk contract:
it proves the explicit extension and schema boundary, fixed embedding dimension,
cosine HNSW, FTS GIN, and content hash provenance. I removed supplementary
string checks for every table and policy column after the clarification to avoid
turning the test into a superficial DDL checklist. The DDL itself contains all
six required tables and the approved/expired filters.

`git diff --check` passed. I verified foreign-key creation order: document's
optional ingestion-run link is added only after `rag.ingestion_run` exists.
The Flyway migration contains no concurrent index statement, which keeps it
transaction-safe; concurrent index operations are isolated in the runbook.

## Concerns

- No PostgreSQL instance with pgvector was supplied, so this task verifies the
  deterministic migration contract and backend suite, not live extension
  installation or DDL execution against a server.
- V16 builds the initial HNSW index transactionally as required by the migration
  contract. For later large-table rebuilds, operators must use the documented
  `CREATE INDEX CONCURRENTLY` procedure outside Flyway.
