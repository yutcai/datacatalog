# DataCatalog — agent instructions

Portfolio backend project: a metadata-driven data catalog (Spring Boot 3, Java 21, PostgreSQL/JSONB, S3 pre-signed URLs, JWT). Small but complete; every design decision must be defensible in an interview.

## Conventions

- **Package by feature**, not by layer: `io.datacatalog.dataset`, `io.datacatalog.version`, shared bits in `common`/`config`.
- **Tests alongside every change**: JUnit + Testcontainers for Postgres-backed slices; Playwright (in `e2e/`) for API end-to-end flows.
- **Liquibase** owns the schema — never `ddl-auto`.
- **Current user comes from the JWT**, never from a request body.
- `FileVersion` rows are immutable; state machine is `PENDING → ACTIVE` only.
- Errors use RFC 7807 `ProblemDetail`.
- Small, logical commits with clear messages.

## Phase 0 scope guard

Only the 7 documented `/v1/datasets*` endpoints. Explicitly out of scope (do not build now): pgvector/semantic search, MCP server, LLM enrichment, SQS workers, Prometheus/Grafana, Terraform, multipart upload, Elasticsearch, Redis.

## Verify

```bash
./gradlew build        # unit + component tests
docker compose up      # full stack: app + Postgres + LocalStack
curl localhost:8083/health
```
