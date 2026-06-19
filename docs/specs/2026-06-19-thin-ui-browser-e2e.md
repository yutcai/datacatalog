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
- **Same-origin serving (no CORS):**
  - *Dev:* Vite dev server (`:5173`) proxies `/v1`, `/health` → `:8083` (hot reload).
  - *Built / CI / E2E:* `vite build` output is served **by Spring from `static/`** at `:8083`,
    so the app and API share an origin. A Gradle task runs the Vite build and copies `dist/`
    into `src/main/resources/static/` (UI + API in one container — clean full-stack deploy story).
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

## Division of labor

I build the thin UI + the same-origin serving + **one sample Playwright UI test** (the login/
auth-fixture exemplar) + the CI job. **You extend the Playwright suite** screen by screen — that's
the practice. I review/pair on tricky bits (upload + route interception, isolation, flakiness).

## Roadmap

New **Phase 0.5 — Thin web UI + browser E2E**, sitting between Phase 0 and Phase 1. The Phase 0
Playwright DoD item is reframed: the happy-path E2E is delivered through the UI here.

## Definition of done

- [ ] `ui/` React+Vite+TS app: login, list+search, create, detail+upload — all wired to the live API
- [ ] Built UI served same-origin by Spring at `:8083`; `docker compose up` serves app + UI + infra
- [ ] One sample Playwright UI test (login + `storageState` fixture) green locally and in CI
- [ ] CI job boots the compose stack and runs the Playwright suite; HTML report uploaded as artifact
- [ ] *(you, ongoing)* remaining UI E2E: search/pagination, create, upload happy-path, edge cases
