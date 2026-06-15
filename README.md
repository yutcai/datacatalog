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

### Developer loop — faster feedback

```bash
docker compose up -d postgres localstack   # infra only
./gradlew bootRun                          # API on :8083
./gradlew test                             # tests boot their own Postgres via Testcontainers
```

No JDK setup needed even for development: the Gradle wrapper is checked in, and the build auto-provisions JDK 21 through the toolchain resolver on first run.

> **About the local credentials:** compose starts Postgres with throwaway `datacatalog`/`datacatalog` credentials that exist only inside your machine's Docker network (override with `DB_PASSWORD=… docker compose up`). Production would never use these — see [Secrets stay out of the repo](#secrets-stay-out-of-the-repo).

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

*To be expanded as each slice lands:*

- **Pre-signed URLs** — why file bytes bypass the app tier
- **Immutable versions with a PENDING → ACTIVE state machine** — why upload is a two-step protocol
- **Sync API, no async pipeline yet** — and where a queue would slot in
- **No multipart upload yet** — implies a practical size cap; how multipart would be added

## AI-assisted development

This project is built with [Claude Code](https://claude.com/claude-code) as a deliberate exercise in AI-assisted engineering: spec-first prompts, incremental vertical slices, tests written alongside every change, and human review of every diff. Commits carry `Co-Authored-By: Claude` trailers; the agent's project instructions live in [CLAUDE.md](CLAUDE.md).
