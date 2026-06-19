# Spec — Thin Web UI + Browser E2E (Phase 0.5)

**Status:** proposed · **Date:** 2026-06-19

## Why

The catalog is API-only today; Playwright is scoped to API flows. For a full-stack portfolio,
the higher-signal demonstration is **browser** E2E. This slice adds a deliberately *thin but
dynamic* web UI so Playwright can exercise real UI-automation skills, while the existing
JUnit/Testcontainers tests keep owning the API contract. One project then tells three stories:
backend, a thin frontend, and **both layers tested** (API component tests + browser E2E).

Principle: **small surface, real behavior.** Skill shows in *how* it's tested, not app size — so
every screen is data-driven, async, and auth-gated, never a static page.

## Scope (4 screens, one primary flow each)

| Screen | Behavior | Browser E2E it enables |
|---|---|---|
| **Login** | username/password → token; gate the rest | auth fixture (`storageState`), login success/failure, redirect when unauthenticated |
| **List + search** | async-load datasets; `q` / `tag` filter; pagination | web-first auto-waiting, dynamic locators, filtered/paginated assertions, empty state |
| **Create dataset** | form → POST; show in list | `getByLabel` fill, client validation + error state |
| **Detail + upload** | metadata/versions; browser PUTs bytes to the **pre-signed URL** | `setInputFiles`, network wait, route interception of the S3 call, PENDING→ACTIVE |

### Out of scope (keep it thin)
Styling beyond minimal, account management, edit-everything screens, responsive/mobile,
state libraries (Redux), SSR. No new backend endpoints — the UI consumes the existing 7.

## Tech & architecture

- **React + Vite + TypeScript** SPA in `ui/`. Routing via `react-router`. Auth token kept in
  memory + `localStorage`; attached as `Bearer` by a tiny fetch wrapper.
- **npm is the UI toolchain** (install React/Vite, run `vite build`) — required regardless; it
  does not replace, and is not replaced by, Gradle. To keep Java/Gradle **untouched**, npm lives
  only inside the UI's Docker build.
- **Same-origin serving (no CORS) via a `ui` compose service:**
  - *Dev:* Vite dev server (`:5173`) proxies `/v1`, `/health` → `:8083` (hot reload).
  - *Built / CI / E2E:* a multi-stage `ui` image (`node` builds the SPA → `nginx` serves it) is
    added to `docker compose`. nginx serves the SPA and reverse-proxies `/v1` and `/health` to
    `app:8083`, so the browser hits **one origin** (the realistic "SPA behind a reverse proxy"
    shape). `docker compose up` brings app + ui + infra together; Gradle stays Java-only.
- **No API-level Playwright tests:** the API contract is already covered by the
  JUnit/Testcontainers component suite; Playwright owns the *browser* layer only (no redundancy).

## Browser E2E (the Playwright work)

- TypeScript Playwright in `e2e/`, `baseURL = http://localhost:8083` (the served SPA).
- Patterns to demonstrate: `storageState` auth fixture (log in once, reuse), **page objects**,
  cross-browser **projects** (chromium/firefox/webkit), parallel workers with per-test isolation
  (unique usernames, mirroring the Java discipline), trace viewer, route interception.
- Coverage = ROADMAP happy path **through the UI** + edges: login failure, unauthenticated
  redirect, 404 dataset, downloading a PENDING version, non-owner forbidden.
- Runs locally against `docker compose up`; a CI job boots compose and runs the suite.

## Delivery: two PRs

- **PR A — UI:** `ui/` React+Vite+TS app + the `ui` compose service (multi-stage Dockerfile +
  nginx same-origin proxy) + docs. Usable via `docker compose up`. No Playwright yet.
- **PR B — browser E2E:** `e2e/` Playwright + one sample test + the CI job, targeting the UI from
  PR A. Merges after PR A (the E2E needs the UI running).

## Division of labor

I build PR A (the thin UI + same-origin serving). In PR B I add the Playwright scaffold + **one
sample UI test** (the login / `storageState` fixture exemplar) + the CI job; **you extend the
suite** screen by screen — that's the practice. I review/pair on tricky bits (upload + route
interception, isolation, flakiness).

## Roadmap

New **Phase 0.5 — Thin web UI + browser E2E**, sitting between Phase 0 and Phase 1. The Phase 0
Playwright DoD item is reframed: the happy-path E2E is delivered through the UI here.

## Definition of done

**PR A (UI):**
- [ ] `ui/` React+Vite+TS app: login, list+search, create, detail+upload — all wired to the live API
- [ ] `ui` compose service serves the SPA same-origin (nginx proxies `/v1`, `/health` to the app); `docker compose up` serves app + ui + infra; Gradle untouched

**PR B (browser E2E):**
- [ ] One sample Playwright UI test (login + `storageState` fixture) green locally and in CI
- [ ] CI job boots the compose stack and runs the Playwright suite; HTML report uploaded as artifact
- [ ] *(you, ongoing)* remaining UI E2E: search/pagination, create, upload happy-path, edge cases
