# Development

How DataCatalog is built: AI-assisted, but spec-first and test-driven, with a human reviewing every change. This document is itself part of the point — it shows *how* the AI tooling is used, not just *that* it was.

## The loop

Each feature is one vertical slice, built in a fixed cycle:

1. **Spec** — a written specification defines the slice before any code ([ROADMAP](ROADMAP.md) holds the Definition of Done per phase).
2. **Design** — non-obvious decisions are settled first and recorded with their rationale (see [Design decisions](../README.md#design-decisions)).
3. **Test first (TDD)** — a failing test is written and *watched fail* before implementation. A test that passes immediately isn't testing anything new.
4. **Implement** — the minimal code to make the test green.
5. **Verify** — full build plus the real stack (`docker compose up`) before anything is called done.
6. **Review & merge** — small, logical commits, each carrying a `Co-Authored-By: Claude` trailer; every slice lands through a pull request so the change, its rationale, and the CI gate are all visible and reviewable.

## Tooling

- **[Claude Code](https://claude.com/claude-code)** drives implementation under direction.
- **[CLAUDE.md](../CLAUDE.md)** constrains the agent — package-by-feature, Liquibase owns the schema, current user from the JWT, the Phase 0 scope guard. The conventions are version-controlled, so the AI works inside the same guardrails every session instead of being re-explained each time.
- **CI** (`./gradlew build` on every push and PR) is the backstop: Testcontainers spins up a real Postgres, so a green check means the contract holds against the engine that runs in production — not an emulator.

## Human-in-the-loop: where judgment mattered

AI assistance is only as good as the supervision around it. These are decisions and corrections from this project that did **not** come from accepting a first draft:

- **A bug the tests caught — and one that nearly hid it.** After adding security, a "duplicate username" request returned `401` instead of the intended `409`. Root cause: a `ResponseStatusException` forwards internally to `/error`, and that dispatch was re-evaluated by the security filter chain as unauthenticated. The fix was to permit the ERROR/FORWARD dispatch types. The telling part: the *wrong-password* test (which also expects `401`) passed by coincidence and would have masked the bug — only the `409` test exposed it. Test-**first**, not test-after, is what surfaced it.
- **Schema-first over speculative entities.** JPA entities are added per slice, only for what that slice consumes, rather than modelling everything up front. The database — owned by Liquibase — stays the single source of truth.
- **The boring-correct option, on purpose.** Testcontainers over an in-memory H2 lookalike, because the schema leans on JSONB, GIN, `text[]`, and check constraints that H2 doesn't honestly emulate. The default `jsonb_ops` GIN opclass over the smaller, faster `jsonb_path_ops`, because search needs key-existence operators, not just containment.
- **Security defaults questioned, not copied.** CSRF protection is disabled — but *because* the API is a stateless bearer-token service with no ambient cookie authority to exploit, not because a tutorial disabled it.

## Conventions

- **Commits** — small and logical, imperative subject line, `Co-Authored-By: Claude` trailer.
- **Branches & PRs** — one slice per branch, merged via pull request after review and a green CI check.
- **Docs track code** — [ROADMAP](ROADMAP.md) Definition-of-Done boxes and [ARCHITECTURE](ARCHITECTURE.md) `(designed)` markers flip as slices land, so the docs never drift from what is actually built.
