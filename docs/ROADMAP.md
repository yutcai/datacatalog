# Roadmap

Each phase has an explicit Definition of Done. A phase ships only when every box is checked — small and complete beats big and half-done. Later phases never start before the previous phase is done.

## Phase 0 — Core catalog service *(in progress)*

A complete, runnable metadata catalog: REST API, S3 pre-signed upload/download, queryable metadata, auth, tests, CI.

**Done when:**

- [ ] All 7 `/v1/datasets*` endpoints work end-to-end: create → request upload → direct PUT to S3 → complete → search → download
- [ ] Current user is derived from the JWT, never from the request body
- [x] Postgres schema managed by Liquibase; metadata queryable via JSONB + GIN index
- [ ] JWT / OAuth2 resource server protects all write endpoints
- [ ] Playwright E2E covers the full happy path plus edge cases: 401 unauthorized, 404 not found, downloading a PENDING version
- [ ] JUnit + Testcontainers cover a Postgres-backed slice
- [x] CI green on GitHub Actions, badge in README
- [x] `docker compose up` starts app + Postgres + LocalStack with one command
- [ ] README: problem statement, architecture diagram, design trade-offs, run instructions
- [ ] Repository public on GitHub
- [ ] *(stretch)* Deployed with a live URL

## Phase 1 — AI layer

Make the catalog usable by humans in natural language and by AI agents directly.

- **Semantic search**: embed metadata + descriptions with pgvector; answer natural-language queries ("production data from last year in region X")
- **LLM metadata enrichment**: on upload, asynchronously suggest tags / description / schema summary
- **MCP server**: expose catalog operations (`search_datasets`, `get_dataset`, …) as MCP tools for AI agents

**Done when:**

- [ ] A natural-language query returns relevant datasets via pgvector similarity search
- [ ] New uploads receive persisted LLM-suggested tags/description
- [ ] MCP tools are callable from a standard MCP client against a running instance
- [ ] All of the above covered by tests

## Phase 1.5 — AI quality & eval harness

Non-deterministic AI features need their own regression strategy.

- **Golden-set retrieval evals**: a curated set of query → expected-datasets pairs; measure precision/recall and hit@k
- **MCP contract tests**: every tool's input/output contract verified

**Done when:**

- [ ] Eval suite runs in CI and fails the build when retrieval metrics regress below thresholds
- [ ] Every MCP tool has a contract test
- [ ] README documents the eval methodology and current metrics

## Phase 2 — Infrastructure & observability

- **Async worker** (SQS): enrichment + indexing through a queue with retry and DLQ
- **Observability**: Prometheus metrics, Grafana dashboard, defined SLOs
- **IaC**: Terraform deployment to AWS

**Done when:**

- [ ] Enrichment jobs flow through the queue; failures retry and dead-letter
- [ ] Dashboard shows request rates, latencies, error rates, queue depth
- [ ] `terraform apply` stands up the full stack in AWS

## Phase 3 — Storage depth

- **Multipart upload with resume**: removes the single-PUT size cap

**Done when:**

- [ ] Large files upload via multipart end-to-end, with tests covering resume after interruption
