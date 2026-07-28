# Production RAG and AI Agent Design

> **Implementation decision (2026-07-28):** The deployed RAG path is deliberately simplified to PostgreSQL native full-text search over approved sources. The earlier pgvector, external embedding, Responses API function-calling, and MCP extension proposals below are deferred and are not runtime dependencies.

## Status and goal

This design upgrades Farmflate's fact-grounded assistant into a production-oriented, read-only agricultural RAG and tool-using agent. It deliberately does **not** make autonomous changes to farm, user, or report data.

The system must provide:

- traceable answers backed by user-authorized facts and versioned, approved knowledge documents;
- bounded native LLM function calling with deterministic policy enforcement;
- hybrid retrieval in the existing PostgreSQL estate via pgvector and full-text search;
- safe degradation when retrieval, the model provider, or an internal service is unavailable; and
- measurable quality, security, latency, and cost behaviour.

## Context and non-goals

The Java/Spring service owns user authentication and user-specific reports. The Python service owns agent orchestration. Today it has a LangGraph workflow, static agricultural dictionaries, a direct Chat Completions request, optional internal-key authentication, and deterministic fallbacks.

This work adds a document-grounded knowledge plane. It does not:

- scrape arbitrary web pages at request time;
- allow the model to write data, execute shell code, or select arbitrary URLs/tools;
- claim that a response is current or official without an approved source; or
- enable an external MCP server by default.

## Chosen architecture

PostgreSQL remains the system of record. pgvector is the semantic index, PostgreSQL full-text search is the lexical index, and the Python service accesses only a dedicated `rag` schema through a least-privilege service account.

```text
approved source manifest
  -> ingestion job -> document / chunk / embedding / provenance tables
  -> PostgreSQL: tsvector + pgvector HNSW

user -> Spring authentication and ownership checks -> FactPackage
  -> Python LangGraph: policy -> retrieve -> bounded tool loop -> validate -> answer
  -> citations, trace id, explicit completed/fallback/insufficient_evidence status
```

The implementation exposes a provider interface so the retrieval layer can be replaced later, but PostgreSQL/pgvector is the only initial production provider. No second vector-database operation is introduced.

## Knowledge lifecycle

### Sources and ingestion

Knowledge is admitted through a version-controlled manifest of official, review-approved source URLs and metadata. An operator-triggered ingestion command downloads only allow-listed HTTPS origins, checks content type and byte limits, records content hash and retrieval time, chunks text, requests embeddings, and writes an immutable ingestion record.

Each source has at least: source id, publisher, source URL, license or usage note, topic, review status, refresh policy, and optional expiry date. A new hash supersedes an old document; failed ingestion leaves the last approved version searchable and records the failure.

Raw fetched material is not committed into Git. Database records preserve the document version and the exact text needed for retrieval/audit under the source's permitted use.

### PostgreSQL model

Flyway owns creation of the following tables in a dedicated schema:

- `rag.source`: approval, publisher, URL, content policy, current version;
- `rag.document`: fetched version, SHA-256 hash, language, status, source metadata;
- `rag.chunk`: document relationship, ordinal, bounded text, token count, `tsvector`, embedding model/version, `vector(1536)` embedding, and searchable metadata;
- `rag.ingestion_run`: actor, status, counters, failure reason, and timing; and
- `rag.eval_case` and `rag.eval_result`: fixed quality scenarios and recorded results.

The migration enables `vector`. A deployment account must have the privilege to create that extension. A separate non-transactional deployment step creates/rebuilds the cosine HNSW index concurrently; normal Flyway table migrations do not block production traffic for a large index build.

`rag.chunk` has a GIN index on `search_vector`, an HNSW cosine index scoped to the configured embedding model/dimension, and ordinary indexes for approved status, source, language, and expiry filters. HNSW is selected for latency-sensitive approximate search; recall is measured against exact-search samples.

### Retrieval policy

For a question, the retriever:

1. normalizes and bounds the query without changing the user's intent;
2. embeds it with a pinned embedding configuration;
3. runs filtered vector and full-text searches independently;
4. combines candidates with Reciprocal Rank Fusion and optionally reranks the small candidate set;
5. removes expired, unapproved, duplicate, or policy-incompatible content; and
6. returns a bounded evidence packet with chunk ids, source URL, title, version/hash, score, and text excerpts.

The initial limits are configurable: 20 candidates per retrieval path, 8 evidence chunks passed to the agent, 2,400 evidence characters per chunk, and a minimum evidence threshold. A result below the threshold returns `insufficient_evidence`; it does not cause a generic LLM answer.

`text-embedding-3-small` at its documented default 1,536 dimensions is the initial embedding configuration, but the model id and dimensions are explicit configuration. A model/dimension change requires a new indexed embedding version and does not mix vectors in an existing HNSW index.

## Agent and tool workflow

### Control flow

The LangGraph workflow becomes:

1. authenticate request context and validate input budgets;
2. classify intent and apply deterministic access policy;
3. obtain authorized user facts and hybrid RAG evidence;
4. call the model through the Responses API with strict function schemas;
5. execute at most two tool rounds and four total read-only calls;
6. validate response structure, citations, fact IDs, numerical claims, and source scope;
7. return a grounded answer, a transparent fallback, or `insufficient_evidence`.

The model never receives credentials, database connections, arbitrary URLs, or unfiltered user records. Existing deterministic fact tools remain available as read-only functions. New `search_knowledge` and `read_evidence_chunk` functions can only access evidence already authorized by the retriever and must respect query, result, and text budgets.

Function calls follow a native request -> tool call -> server-side validated execution -> tool output -> final response loop. Tool arguments are parsed against strict JSON schemas, unknown fields fail, and every execution has a trace span. The model cannot select a tool absent from the per-intent allow-list.

The initial release deliberately enables no live MCP server; bounded Function calling is the accepted tool integration. MCP is an extension boundary for a later release, not a default dependency. A future MCP tool must be explicitly registered with an approved server identity, read-only capability declaration, timeout, per-tool schema, and egress allow-list. No generic remote-MCP URL is accepted from configuration or user input.

### Prompt and output contract

System instructions distinguish policy from untrusted question, history, retrieved text, and tool output. Retrieved documents are quoted as data, never instructions. The prompt requires Korean responses, evidence-backed claims, no treatment dosage or diagnosis, and an explicit uncertainty statement where evidence is incomplete.

The final structured response contains: answer text, status, evidence/citation IDs, source descriptors, used fact IDs, bounded numerical claims, safety notice, trace ID, and degradation reason where applicable. The server verifies that every cited source and fact is in the authorized packet, that numerical claims are represented by authorized evidence/facts, and that the answer does not claim unsupported crops or risks. Validation failure uses the fact-only fallback rather than the model response.

## Security and privacy

- The Python internal API fails closed when its secret is absent; Java has no publicly committed secret default.
- Spring remains the user-facing authorization boundary. Python receives only a sanitized FactPackage and uses a PostgreSQL role limited to the `rag` schema; it cannot read user/report tables.
- Java enforces per-user request budgets. Python enforces global and per-model concurrency, request-size limits, and output-size limits.
- Source ingestion permits only approved hosts and content types, records hashes, and rejects active or oversized payloads.
- Logs exclude API keys, raw authorization headers, and full prompt/document bodies. Trace payloads store identifiers, sizes, scores, selected tools, latency, and redacted failure classes.
- UI rendering treats model output as untrusted text; no model output is evaluated as HTML, JavaScript, SQL, or a command.
- Supply-chain review is a release gate. The current frontend lockfile reports one critical, three high, and three moderate advisories through Vitest/Vite and React Router. Dependency remediation is a separate compatibility-tested change; no blind `npm audit fix --force` is permitted.

## Resilience and observability

The model client has explicit connect/read timeouts, bounded exponential retry for retryable provider errors, a circuit breaker, and a semaphore. Retrieval has its own timeout and never falls back to arbitrary external search. Java reports Python outage degradation as `fallback`, not `completed`.

The services emit JSON logs and metrics for request status, RAG hit/insufficient-evidence rate, source/version usage, retrieval and model latency, tool selection/denial, retry/circuit state, fallback count, token/cost estimates when supplied by the provider, and rate-limit rejects. Every request carries a trace ID through Java, Python, retrieval, and tool spans.

## Quality gates and release criteria

The repository contains deterministic evaluation cases for retrieval relevance, citation correctness, authorized-source filtering, multilingual Korean queries, prompt injection, source poisoning markers, unsupported actions, tool argument validation, numerical hallucination, ownership boundaries, provider timeout, circuit-open behaviour, and fallback status.

CI runs unit and integration tests without live provider credentials. A separately gated staging suite runs the ingestion and Responses API flow with non-production secrets and approved test documents. Release requires:

- zero critical security/evidence failures in the evaluation suite;
- no cross-user or unapproved-source leakage;
- retrieval recall and citation precision measured against the versioned golden set;
- configurable latency/error/cost thresholds met in staging; and
- no unresolved critical/high dependency advisory unless a time-bounded, approved exception documents compensating controls; and
- production migration/rollback and extension-installation checks completed by an authorized database operator.

## Implementation sequencing

1. Add schema, least-privilege configuration, source manifest, and test fixtures.
2. Implement ingestion, embedding, hybrid retrieval, provenance, and retrieval evaluations.
3. Replace the direct LLM call with bounded Responses API function orchestration and strict validation.
4. Add fail-closed auth, rate/concurrency limits, circuit breaking, true streaming, metrics/tracing, and transparent fallback status.
5. Run the full Python, Spring, and frontend suites plus staged database/provider checks.

## Operational prerequisites

The deployment PostgreSQL instance must support pgvector and allow `CREATE EXTENSION vector` during an approved migration. Production needs separate migration, ingestion, and read-only RAG database credentials, plus an OpenAI API key for embeddings/model calls. This repository never stores those secrets.
