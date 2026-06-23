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
    API -->|metadata queries| PG[("PostgreSQL<br/>JSONB + GIN")]
    API -->|pre-signed URLs| S3[(S3 / LocalStack)]
    Client -->|"PUT / GET file bytes<br/>directly via pre-signed URL"| S3
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

Java 21 · Spring Boot 3 · Spring Security (JWT / OAuth2 resource server) · Spring Data JPA · PostgreSQL (JSONB + GIN) · Liquibase · AWS S3 (LocalStack for local dev) · Gradle · JUnit + Testcontainers · React + Vite (thin web UI) · Playwright (browser E2E) · GitHub Actions · Docker Compose

## Running locally

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine with Compose v2).

### One command — full stack

```bash
git clone <this-repo> && cd datacatalog
docker compose up
curl localhost:8083/health    # → {"status":"UP",...}
```

This compiles the app inside Docker (multi-stage build) and starts the API on **:8083**, Postgres 16 on **:5432**, LocalStack S3 on **:4566**, and a thin React **web UI on :3000**. Liquibase migrates the schema automatically on startup.

### Use the web UI

A thin React UI lives at **http://localhost:3000** once the stack is up. **The backend must be running** — the UI is a static SPA that gets all its data from the API, so on its own it loads but can't log in, search, or upload. It's a deliberately thin surface over the same API, primarily there to host browser end-to-end tests (see [docs/specs](docs/specs/2026-06-19-thin-ui-browser-e2e.md)).

Two ways to run it:

- **Just try it (recommended):** `docker compose up` already starts the UI alongside the API, Postgres, and LocalStack — open **http://localhost:3000**. nginx serves the SPA and reverse-proxies the API, so the browser talks to a single origin (no CORS).
- **Develop it (hot reload):** start the backend only — `docker compose up -d app postgres localstack` — then `cd ui && npm install && npm run dev` and open **http://localhost:5173**. Vite proxies `/v1` and `/health` to the app and reloads on save.

Once it's open, the happy path: **register** → **New dataset** → **search** the list → open a dataset → **Upload** a file (the browser PUTs it straight to S3 via a pre-signed URL) → **Download** it back → edit the **description** (owner-only). Start from a clean slate any time with `docker compose down -v`.

> **Local data lifecycle:** Postgres has a named volume, so catalog records survive restarts; LocalStack does not, so **uploaded file bytes are wiped whenever its container is rebuilt** (`up --build`, `down`). After a rebuild, an old version may download as "no longer in local storage" — re-upload it, or `docker compose down -v` for a consistent clean slate. This is a local-dev artifact only; real S3 is durable.

**Try the API in your browser:** open **http://localhost:8083/swagger-ui.html** — register a user, call `/v1/auth/token`, click **Authorize** to paste the token, then exercise the protected endpoints. The raw OpenAPI spec is at `/v3/api-docs`. (These are dev conveniences and are disabled under the `prod` profile — run production with `SPRING_PROFILES_ACTIVE=prod`.)

### Walk through the API (curl)

Prefer the terminal? The full happy path — auth, the two-step upload and download, then search and a partial update. (Uses `jq` to capture ids/URLs; or run each call alone and copy the values by hand.)

```bash
# 1. Register + exchange credentials for a JWT
curl -s -X POST localhost:8083/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"s3cret-pw"}' -w '-> %{http_code}\n'
TOKEN=$(curl -s -X POST localhost:8083/v1/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"s3cret-pw"}' | jq -r .accessToken)

# 2. Create a dataset — the owner is taken from the token, never the body
DATASET=$(curl -s -X POST localhost:8083/v1/datasets -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"sales-2025","tags":["sales","emea"],"metadata":{"region":"emea"}}' | jq -r .id)

# 3. Request an upload -> a PENDING version + a pre-signed S3 PUT URL
REQUEST=$(curl -s -X POST localhost:8083/v1/datasets/$DATASET/versions -H "Authorization: Bearer $TOKEN")
VERSION=$(echo "$REQUEST" | jq -r .versionId)
PUT_URL=$(echo "$REQUEST" | jq -r .uploadUrl)

# 4. Upload bytes DIRECTLY to S3 with the pre-signed URL — they never pass through the app.
#    You don't create the S3 key yourself: request-upload already minted it; the PUT just fills it.
#    (swap in a real file with `--upload-file ./some.csv`; verify it landed via "Connect to S3" below)
echo -n 'hello data catalog' | curl -s -o /dev/null -w 'upload -> %{http_code}\n' \
  -X PUT --data-binary @- "$PUT_URL"

# 5. Complete -> the server HEADs the object and flips the version PENDING -> ACTIVE
curl -s -X POST localhost:8083/v1/datasets/$DATASET/versions/$VERSION/complete \
  -H "Authorization: Bearer $TOKEN"; echo

# 6. Download -> a pre-signed GET URL; fetch the same bytes back
DL_URL=$(curl -s localhost:8083/v1/datasets/$DATASET/versions/$VERSION/download \
  -H "Authorization: Bearer $TOKEN" | jq -r .downloadUrl)
curl -s "$DL_URL"; echo    # -> hello data catalog

# 7. Search -> filter by free-text / tag / owner, offset-paginated {items,page,limit,total}
curl -s "localhost:8083/v1/datasets?q=sales&tag=emea&page=0&limit=10" \
  -H "Authorization: Bearer $TOKEN" | jq '{total, names: [.items[].name]}'

# 8. Patch -> partial update; metadata is MERGED by key, and only the owner may modify
curl -s -X PATCH localhost:8083/v1/datasets/$DATASET \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"description":"reviewed for Q3","metadata":{"reviewed":true}}' \
  | jq '{description, metadata}'    # region kept, reviewed added

# Auth is enforced: the same call without a token -> 401
curl -s -o /dev/null -w 'no token -> %{http_code}\n' localhost:8083/v1/datasets/$DATASET
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

With the stack running (`docker compose up`), Postgres is exposed on **localhost:5432**. Connection details (local-dev defaults):

| Setting | Value |
|---|---|
| host | `localhost` |
| port | `5432` |
| database | `datacatalog` |
| user | `datacatalog` |
| password | `datacatalog` |

**Option 1 — no install needed** (psql runs inside the container):

```bash
docker compose exec postgres psql -U datacatalog -d datacatalog
```

**Option 2 — psql on your host:**

```bash
psql "postgresql://datacatalog:datacatalog@localhost:5432/datacatalog"
```

**Option 3 — a GUI client** (DBeaver, TablePlus, IntelliJ Database, pgAdmin): create a PostgreSQL connection with the values from the table above.

Once connected, look at what the API created:

```sql
\dt                                                   -- list tables
select username, created_at from users;               -- registered users
select id, name, tags, metadata from datasets;        -- catalog entries (JSONB metadata)
select dataset_id, version_number, state, size_bytes  -- versions: PENDING vs ACTIVE
  from file_versions order by created_at;
select * from databasechangelog;                      -- what Liquibase migrations ran
```

### Connect to S3 (LocalStack)

File bytes live in S3 — locally, a [LocalStack](https://www.localstack.cloud/) container exposed on **localhost:4566**, holding a `datacatalog` bucket. The app never proxies bytes; clients PUT/GET them directly via pre-signed URLs. To inspect what actually landed, point the AWS CLI at the local endpoint (local-dev defaults):

| Setting | Value |
|---|---|
| endpoint | `http://localhost:4566` |
| region | `us-east-1` |
| bucket | `datacatalog` |
| access key | `test` |
| secret key | `test` |

**Option 1 — no install needed** (`awslocal` ships inside the LocalStack container, pre-pointed at the local endpoint):

```bash
docker compose exec localstack awslocal s3 ls s3://datacatalog --recursive
```

**Option 2 — AWS CLI on your host** (every object key is `datasets/<datasetId>/versions/<uuid>`):

```bash
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=us-east-1
alias awslocal='aws --endpoint-url http://localhost:4566'

awslocal s3 ls s3://datacatalog --recursive          # list uploaded objects
awslocal s3 cp s3://datacatalog/<key> -              # stream an object's bytes to stdout
awslocal s3api head-object --bucket datacatalog --key <key>   # size + ETag the app records on complete
```

This is how to confirm a pre-signed PUT really landed: after step 4 of the [curl walkthrough](#walk-through-the-api-curl), `s3 ls` shows the object — and `complete` only flips the version to ACTIVE because the server sees the same object via a HEAD request.

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
| GET | `/v1/datasets/{id}/versions` | List the dataset's ACTIVE versions, newest first |
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

### Search, filtering, and pagination

`GET /v1/datasets` composes three optional, AND-combined filters and returns an offset-paginated envelope (`{ items, page, limit, total }`). `q` is a case-insensitive substring over **name and description** — the free-text prose; `tag` and `metadata` keep their own precise paths (`tags @> ARRAY[tag]` on the GIN index; JSONB containment), because folding structured facets into a fuzzy match both blurs the semantics and gives up the index. `owner` filters by username, resolved to an id first. Pagination is **offset** (`page`/`limit`) — simple and correct for cataloging at this scale; the known trade-off is that deep offsets scan-and-skip and a concurrent insert can shift rows across pages. **Keyset** (seek after the last `created_at`/`id`) is the answer at large scale and is the natural upgrade — same index, a different `WHERE`. Owner usernames for a page are resolved in a single batched lookup, not per row, to avoid an N+1.

### Partial updates and owner-scoped writes

`PATCH /v1/datasets/{id}` is a partial update: any omitted field is left untouched, and `metadata` is **merged by key** (a true patch — existing keys survive) rather than replaced. Writes are **owner-scoped** — only the dataset's owner, taken from the token, may modify it; a non-owner gets `403`, a missing dataset `404` (checked before ownership, so existence isn't leaked). `updated_at` is maintained by a `BEFORE UPDATE` **database trigger**, so it stays correct no matter which client issues the write — the DB owns its timestamps, consistent with the rest of the schema.

*To be expanded as each slice lands:*

- **Sync API, no async pipeline yet** — and where the `dataset.version.activated` event + a consumer slot in (Phase 1)
- **No multipart upload yet** — implies a practical size cap; how multipart would be added (Phase 3)

## AI-assisted development

This project is built with [Claude Code](https://claude.com/claude-code) as a deliberate exercise in AI-assisted engineering: spec-first prompts, incremental vertical slices, tests written alongside every change, and human review of every diff. Commits carry `Co-Authored-By: Claude` trailers; the agent's project instructions live in [CLAUDE.md](CLAUDE.md); and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) documents the workflow in full — including where the AI's first attempt was wrong and how it was caught.
