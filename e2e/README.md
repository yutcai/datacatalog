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

## Debugging

The stack must be up first (`docker compose up -d`) — the tests drive the served UI at `BASE_URL`.

**From the CLI:**

- `npx playwright test --ui` — **UI Mode**: time-travel through steps, watch mode, built-in *Pick
  locator*. The best place to start.
- `npx playwright test download.spec.ts --debug` — **Inspector**: pause on each action, step
  through, try locators live.
- `await page.pause()` in a test — drops into the Inspector on the live page to explore locators
  interactively (beats `console.log`).
- `npx playwright show-report` / `npx playwright show-trace trace.zip` — **Trace Viewer**:
  post-mortem DOM snapshots + network + console per action. This is the `playwright-report` the CI
  job uploads as an artifact.
- `npx playwright codegen localhost:3000` — record actions into test code and get recommended
  locators.

**From an IDE** (the tests are auto-recognized — run/debug a single test from the gutter, no config):

- **VS Code** — the official *Playwright Test for VSCode* extension is the most full-featured:
  run/debug from the gutter, Pick locator, record, inline trace.
- **JetBrains (IntelliJ IDEA Ultimate / WebStorm)** — Playwright tests get green run/debug gutter
  icons (2023.3+); breakpoints in TS and results are integrated. Install the **Test Automation**
  plugin for role-based locator generation and a Web Inspector that validates a locator points at
  the right element. Needs a configured Node interpreter. *(IntelliJ Community has no JS/TS
  support — use the CLI or VS Code there.)*

**Gotcha:** if the saved login (`playwright/.auth/user.json`) goes stale while debugging, delete it
to force `auth.setup.ts` to re-run: `rm -rf playwright/.auth`.

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
