# Browser E2E (Playwright)

End-to-end tests that drive the **web UI** in a real browser. The API contract is covered by the
JUnit/Testcontainers suite; these own the UI layer. They run against the served UI at
`http://localhost:3000` (nginx serves the SPA and proxies the API — one origin).

## Run

```bash
docker compose up -d            # from the repo root: app + UI + Postgres + LocalStack
cd e2e
npm install
npx playwright install chromium
npm test                        # headless
npx playwright test --ui        # interactive UI mode — great while writing tests
npm run report                  # open the HTML report
```

Override the target with `BASE_URL` (e.g. the Vite dev server `http://localhost:5173`).

## Layout

- `auth.setup.ts` — a **setup project** that registers + logs in once through the UI and saves
  `storageState` (the JWT lives in `localStorage`). Other tests depend on it and start
  authenticated, so they skip the login step.
- `tests/auth.spec.ts` — **sample #1**: reusing the saved login.
- `tests/download.spec.ts` — **sample #2**: upload then download, asserting the **file actually
  arrives** (`waitForEvent('download')` + byte check). This is the pattern that catches the
  popup-block "Download did nothing" bug — assert the *outcome*, not the mechanism. A shallow
  click test, or a `waitForEvent('popup')` test, would false-green.

## Extending (the practice)

Add `tests/<feature>.spec.ts`. Each test starts authenticated via `storageState`; isolate data
by creating your own dataset per test (the catalog list is shared). Good next targets:

- search + filter + pagination
- create-form validation (empty name, bad JSON metadata)
- 404 on an unknown dataset id; unauthenticated redirect to `/login`
- non-owner cannot edit (403 surfaced in the UI)
- the version history shows uploaded versions and downloads any of them
