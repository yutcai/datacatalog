# Semantic search over dataset metadata (pgvector)

*Phase 1, slice A — the first piece of the AI layer.*

## Goal

Let users find datasets by **meaning**, not just keyword overlap — "European revenue figures from last year" should surface `sales-2026` even without those exact words. Today's `GET /v1/datasets?q=` is a case-insensitive substring match over name + description; semantic search complements it: embed each dataset's text into a vector, embed the query the same way, and rank by vector similarity.

## Why pgvector (not a separate vector DB)

The same reasoning that put metadata in JSONB rather than a document store: keep it in the database we already run. `pgvector` is a Postgres extension adding a `vector` column type, similarity operators (`<=>` cosine distance), and an approximate-nearest-neighbor index (HNSW). Embeddings live alongside the relational data — transactional, joinable with owners and versions, one backup and one system to operate. A dedicated vector DB (Pinecone / Weaviate / Qdrant) earns its keep at large scale or across many stores; at this scale it is operational overhead for no gain.

## Design

### The embedding abstraction (dependency inversion)

Embeddings come from a model, which is an external, slow, costly dependency. To keep the whole feature **buildable and testable with nothing external**, embedding generation sits behind an interface:

```java
interface EmbeddingClient {
    float[] embed(String text);   // a fixed-dimension vector
    int dimensions();
}
```

- **`FakeEmbeddingClient`** — deterministic *and* similarity-preserving. Naively hashing the whole string would make two unrelated texts as likely to collide as two related ones, so ranking tests would assert an artifact of the hash, not meaning. Instead it tokenizes the text and maps each token onto dimensions (hashed bucket + sign, then L2-normalize), so texts that **share words land closer** in vector space — "quarterly sales" sits nearer "sales revenue" than "childcare rota". That is what makes the ranking assertions meaningful rather than tautological. No API key, no network, no cost, so it's the default in tests and local dev.
- **A real provider** — added last, behind config: OpenAI `text-embedding-3-small` (or a local model). Selected by a property; the Fake stays the default, so `./gradlew build` and `docker compose up` keep working with nothing external.

This is the point of the slice's structure: the *similarity machinery* (schema, index, query, endpoint) is fully exercised against **real pgvector** using the Fake embedder. Only the final step swaps in real vectors.

### Schema (Liquibase)

- Swap the Postgres image to `pgvector/pgvector:pg16` — a drop-in (same Postgres, extension available). Testcontainers uses the same image, so tests run against real pgvector.
- Changeset: `CREATE EXTENSION IF NOT EXISTS vector;`
- Add `datasets.embedding vector(1536)` — nullable (a row may not be embedded yet).
- HNSW index: `USING hnsw (embedding vector_cosine_ops)` for fast ANN search.

Dimension **1536** matches the target real provider (OpenAI `text-embedding-3-small`); the Fake produces 1536-dim vectors too. The column dimension is fixed at schema time — see *Decisions to confirm*.

### What gets embedded

The human-meaningful text: `name`, `description`, and `tags`, joined into one string. Not the raw metadata JSON — keys and punctuation add noise, the same reasoning behind why `q` searches name + description only.

### Populating embeddings

- **On write (synchronous, this slice):** creating a dataset, or updating any of the embedded fields (name / description / tags), recomputes its embedding. A metadata-only `PATCH` skips it — the embedded text hasn't changed, so re-embedding would be wasted work.
- **Backfill:** embeds pre-existing rows. The project has no admin role yet, so this is *not* a public API endpoint — it is exposed as an Actuator/management endpoint (or a config-gated startup runner), off the public surface.
- **Later (slice B — events):** the ROADMAP's event backbone moves embedding off the request path — a consumer reacts to `dataset.version.activated` / `dataset.updated`. The `EmbeddingClient` seam and the synchronous call site are exactly where that consumer will plug in. Noted here, not built here.

### Query + endpoint

- Repository: `SELECT ... WHERE embedding IS NOT NULL ORDER BY embedding <=> :queryVec LIMIT :k` (cosine distance via `<=>` + `vector_cosine_ops`).
- `GET /v1/datasets/search/semantic?q=<text>&k=<n>` — embeds `q`, returns the *k* nearest datasets, each with a similarity score. Protected like every other read. The response is a **new top-k DTO** (`{ items: [{ …datasetSummary, score }] }`), deliberately *not* the paginated `DatasetPage`: there is no `page` / `limit` / `total`, just a ranked list with scores.

### Testing

- Testcontainers with the pgvector image — real extension, real HNSW, real `<=>`. Same "test the database I actually run" principle as the rest of the project.
- The deterministic Fake embedder makes ranking assertions stable: seed a few datasets, query, assert the expected order.

## Implementation steps (incremental, one PR each)

1. This spec.
2. pgvector image + `CREATE EXTENSION` changeset + Testcontainers wiring + test.
3. `embedding` column + JPA mapping (vector ↔ `float[]` converter) + round-trip test.
4. `EmbeddingClient` interface + `FakeEmbeddingClient` + test.
5. Populate the embedding on create/update (sync) + test.
6. Backfill path for existing rows + test.
7. Similarity query + HNSW index + Testcontainers ranking test.
8. `/v1/datasets/search/semantic` endpoint + component test.
9. Real provider (OpenAI `text-embedding-3-small`) behind config; Fake stays the default.
10. UI natural-language search (optional) + README / ARCHITECTURE / design-decisions.

## Out of scope (deferred)

- Async / event-driven embedding (slice B — Kafka/Redpanda).
- LLM metadata enrichment; MCP server (later Phase 1 slices).
- Retrieval eval harness / precision-recall metrics (Phase 1.5).
- Hybrid search (blending keyword and vector scores).

## Decisions to confirm

1. **Embedding dimension / target provider:** `1536` (OpenAI `text-embedding-3-small`) vs `384` (a local MiniLM model — no API key ever, but a heavier in-process ONNX dependency). Fixes the column dimension; needed by step 3. *(Automated review leaned 384/local, to keep the "no external dependency" property true even for the real embedder — a genuine toss-up; owner to pick.)*
2. **Endpoint shape:** a **separate** `/v1/datasets/search/semantic` endpoint (chosen) vs a `mode=semantic` parameter on the existing search endpoint. *(Automated review concurred: separate — the mechanics, params, and response shape all differ.)*
