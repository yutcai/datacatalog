# DataCatalog

[![CI](https://github.com/yutcai/datacatalog/actions/workflows/ci.yml/badge.svg)](https://github.com/yutcai/datacatalog/actions/workflows/ci.yml)

A metadata-driven data catalog: store data files in S3 together with rich, queryable metadata in PostgreSQL — upload/download via pre-signed URLs, secured with JWT, with search, filtering, pagination, and immutable versioning.

> **Status:** Phase 0 in progress — scaffolding complete, endpoints under construction. See the [roadmap](docs/ROADMAP.md) for each phase's Definition of Done.

## The problem

Data files scattered across shared drives and buckets are effectively lost: nobody knows what exists, who owns it, or which version is current. DataCatalog gives every file a catalog entry with queryable metadata, so datasets can be found, versioned, and downloaded through one API.

## Architecture

```mermaid
flowchart LR
    Client -->|JWT| API[Spring Boot API]
    API -->|metadata queries| PG[(PostgreSQL\nJSONB + GIN)]
    API -->|pre-signed URLs| S3[(S3 / LocalStack)]
    Client -->|PUT / GET file bytes\ndirectly via pre-signed URL| S3
```

File bytes never pass through the application tier — the API issues pre-signed S3 URLs and the client transfers directly to/from object storage. Deeper dive: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Data model

```mermaid
erDiagram
    users ||--o{ datasets : owns
    datasets ||--o{ file_versions : "has immutable versions"

    users {
        uuid id PK
        text username UK
        timestamptz created_at
    }
    datasets {
        uuid id PK
        text name
        uuid owner_id FK
        text team
        text description
        text_array tags "GIN-indexed"
        jsonb metadata "GIN-indexed, queryable"
        uuid latest_version_id FK
        timestamptz created_at
        timestamptz updated_at
    }
    file_versions {
        uuid id PK
        uuid dataset_id FK
        int version_number "unique per dataset"
        bigint size_bytes
        text checksum
        text s3_key
        text state "PENDING or ACTIVE"
        timestamptz created_at
    }
```

The schema is owned by [Liquibase changesets](src/main/resources/db/changelog/) — see [Design decisions](#design-decisions).

## Tech stack

Java 21 · Spring Boot 3 · Spring Security (JWT / OAuth2 resource server) · Spring Data JPA · PostgreSQL (JSONB + GIN) · Liquibase · AWS S3 (LocalStack for local dev) · Gradle · JUnit + Testcontainers · Playwright (API E2E) · GitHub Actions · Docker Compose

## Running locally

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine with Compose v2).

### One command — full stack

```bash
git clone <this-repo> && cd datacatalog
docker compose up
curl localhost:8083/health    # → {"status":"UP",...}
```

This compiles the app inside Docker (multi-stage build) and starts three containers: the API on **:8083**, Postgres 16 on **:5432**, and LocalStack S3 on **:4566**. Liquibase migrates the schema automatically on startup.

**Try the API in your browser:** open **http://localhost:8083/swagger-ui.html** — register a user, call `/v1/auth/token`, click **Authorize** to paste the token, then exercise the protected endpoints. The raw OpenAPI spec is at `/v3/api-docs`. (These are dev conveniences and are disabled under the `prod` profile — run production with `SPRING_PROFILES_ACTIVE=prod`.)

### Walk through the API (curl)

Prefer the terminal? The same happy path:

```bash
# 1. Register a user — inserts a row in `users`, password stored BCrypt-hashed
curl -s -X POST localhost:8083/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"s3cret-pw"}' -w '-> %{http_code}\n'

# 2. Exchange credentials for a JWT, capture it into $TOKEN
#    (needs jq; or run the call alone and copy "accessToken" from the JSON)
TOKEN=$(curl -s -X POST localhost:8083/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"s3cret-pw"}' | jq -r .accessToken)

# 3. Call a protected endpoint with the bearer token -> 200 + your user
curl -s localhost:8083/v1/me -H "Authorization: Bearer $TOKEN"; echo

# 4. Without a token -> 401, proving the endpoint is secured
curl -s -o /dev/null -w '/v1/me without token -> %{http_code}\n' localhost:8083/v1/me
```

### Developer loop — faster feedback

```bash
docker compose up -d postgres localstack   # infra only
./gradlew bootRun                          # API on :8083
./gradlew test                             # tests boot their own Postgres via Testcontainers
```

No JDK setup needed even for development: the Gradle wrapper is checked in, and the build auto-provisions JDK 21 through the toolchain resolver on first run.

> **About the local credentials:** compose starts Postgres with throwaway `datacatalog`/`datacatalog` credentials that exist only inside your machine's Docker network (override with `DB_PASSWORD=… docker compose up`). Production would never use these — see [Secrets stay out of the repo](#secrets-stay-out-of-the-repo).

### Connect to the database

```bash
# psql inside the running container
docker compose exec postgres psql -U datacatalog -d datacatalog

# …or from any external client (psql, DBeaver, TablePlus, IntelliJ):
#   host=localhost  port=5432  db=datacatalog  user=datacatalog  password=datacatalog
psql "postgresql://datacatalog:datacatalog@localhost:5432/datacatalog"
```

Useful once connected: `\dt` (list tables), `select username, created_at from users;`, `select id, name, metadata from datasets;`. The schema and what ran is recorded in `databasechangelog` (Liquibase). Connection values are the local-dev defaults noted above.

## API (Phase 0)

Authentication:

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/auth/register` | Create a user (password stored BCrypt-hashed) |
| POST | `/v1/auth/token` | Exchange username/password → signed JWT |
| GET | `/v1/me` | Current user, derived from the JWT (protected) |

Catalog (all protected — require a `Bearer` token):

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/datasets` | Create catalog entry → `datasetId` |
| POST | `/v1/datasets/{id}/versions` | Request upload → pre-signed PUT URL |
| POST | `/v1/datasets/{id}/versions/{vid}/complete` | Record size/checksum, state → ACTIVE |
| GET | `/v1/datasets/{id}` | Dataset + latest version + metadata |
| GET | `/v1/datasets?q=&tag=&owner=&page=&limit=` | Search / filter, paginated |
| GET | `/v1/datasets/{id}/versions/{vid}/download` | Pre-signed GET URL |
| PATCH | `/v1/datasets/{id}` | Update metadata |

## Design decisions

### PostgreSQL + JSONB with a GIN index

Dataset metadata is user-defined and varies per dataset, so it cannot live in fixed columns — but it must stay queryable. A `jsonb` column with a GIN index gives schemaless writes and indexed containment queries (`metadata @> '{"region": "emea"}'`) in the same store as the relational data: transactional consistency with datasets/versions, joins for free, and no second system to operate. The alternatives both lose at this scale — an EAV table turns every multi-key filter into self-joins, and a document DB adds an operational dependency while giving up joins. The default `jsonb_ops` opclass was chosen over the smaller `jsonb_path_ops` because search also needs key-existence operators, not just containment.

### Secrets stay out of the repo

The only credentials in this repository are throwaway defaults for the local Docker network. The app reads every connection setting from environment variables (`DB_HOST`, `DB_USER`, `DB_PASSWORD`, …), so a production deployment injects real values at runtime — typically from AWS Secrets Manager or SSM Parameter Store, rotated without a code change. Better still, the password can disappear entirely: RDS supports IAM database authentication, and S3 access in production uses IAM roles, not access keys. The principle: the repo defines *which* configuration exists, the environment supplies its *values*.

### Liquibase owns the schema

The schema is defined in versioned, reviewable SQL changesets that run automatically on startup; the `databasechangelog` table records exactly what ran in every environment. Hibernate is pinned to `ddl-auto: validate`, so entity/schema drift fails fast at boot instead of being silently "fixed" in production. Every changeset declares a rollback. Tables are plain DDL on purpose: JSONB, GIN indexes, and check constraints are Postgres features, and hiding them behind an abstraction layer would only obscure what is actually deployed.

### Stateless JWT auth, issuer decoupled from validation

Every endpoint except `/health` and `/v1/auth/**` requires a signed JWT (RS256); the current user is taken from the verified token `sub`, never from a request body. The app validates tokens as a standard Spring Security OAuth2 *resource server*. It also issues them — `/v1/auth/token` signs with a per-instance RSA key — but issuance and validation are deliberately decoupled: in production the issuer becomes a real identity provider (Cognito/Auth0/Keycloak) addressed by `issuer-uri`, and the validation half of the code does not change. Passwords are stored BCrypt-hashed; the session policy is stateless (no server-side session, so CSRF protection — which guards cookie auth — is disabled by design).

**Current grant model:** `/v1/auth/token` is a direct username/password exchange — the shape of OAuth2's *resource-owner-password* grant, used here as a self-contained stand-in, **not** a full authorization server. (That grant is deprecated in OAuth 2.1 precisely because the app sees the password; the *resource server* half above is the production-grade part.) Browser-redirect social login — OAuth2 **Authorization Code + PKCE** with an external IdP such as Google, where the app never sees the password — is the documented next step in the [roadmap](docs/ROADMAP.md).

### Pre-signed URLs and the two-step upload

File bytes never pass through the application tier. To upload, the client calls `request-upload`, which creates a **PENDING** version and returns a pre-signed S3 PUT URL; the client transfers the bytes straight to S3; then `complete` runs. Because the server never witnesses that transfer, `complete` doesn't trust the client — it **HEADs the object** and only flips PENDING → ACTIVE if the bytes are really there, recording the server-observed size and checksum (ETag). An abandoned upload just stays PENDING: invisible to reads, never downloadable, harmless (production would expire orphans with an S3 lifecycle rule). Download issues a pre-signed GET URL for ACTIVE versions only. One subtlety the local stack makes concrete: a pre-signed URL's host must be reachable *by the client*, which can differ from the address the app uses to reach S3 — so the presigner uses a separate public endpoint.

*To be expanded as each slice lands:*

- **Sync API, no async pipeline yet** — and where the `dataset.version.activated` event + a consumer slot in (Phase 1)
- **No multipart upload yet** — implies a practical size cap; how multipart would be added (Phase 3)

## AI-assisted development

This project is built with [Claude Code](https://claude.com/claude-code) as a deliberate exercise in AI-assisted engineering: spec-first prompts, incremental vertical slices, tests written alongside every change, and human review of every diff. Commits carry `Co-Authored-By: Claude` trailers; the agent's project instructions live in [CLAUDE.md](CLAUDE.md); and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) documents the workflow in full — including where the AI's first attempt was wrong and how it was caught.
