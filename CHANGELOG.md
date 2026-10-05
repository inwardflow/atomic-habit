# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-10-03

First tagged release.

### Upgrade notes
- **`SPRING_JWT_SECRET` is required** with the `prod` profile (Base64/hex, at least 32 bytes, e.g.
  `openssl rand -hex 32`); the application refuses to start without it.
- **Database schema is now managed by Flyway** in `prod`. Existing databases created by
  `ddl-auto=update` are baselined at version 1 automatically and only receive the new index migration;
  fresh databases are created from `V1__init.sql`. docker-compose now defaults
  `SPRING_JPA_HIBERNATE_DDL_AUTO` to `validate`; remove any `update` override you set.
- **Everyone is signed out once** after upgrading, because refresh tokens are now stored hashed.
- Access tokens are no longer accepted in the `?token=` query parameter; send
  `Authorization: Bearer <token>`.
- `AGENTSCOPE_BASE_URL` in docker-compose was renamed to the variable the app actually reads,
  `AGENTSCOPE_MODEL_BASE_URL`.

### Security
- JWTs signed with an unknown key are now rejected as unauthenticated. Previously the
  `SignatureException` escaped the authentication filter.
- AI Coach tools no longer act on a model-supplied `email` argument. The user is resolved from
  server-side state only, so a prompt-injected address cannot read or modify another account.
- A valid token for a deleted user is treated as anonymous instead of causing a server error.
- The `prod` profile refuses to start without `SPRING_JWT_SECRET`, and weak (< 256-bit) secrets
  fail at startup instead of on first login.
- The AG-UI `threadId` was chosen by the client and trusted for tool calls, so any signed-in user
  could act on another user's data or resume their conversation by sending `user-<id>`. The server
  now pins every AG-UI run to the authenticated user's thread, and long-term memory no longer trusts
  client-supplied message metadata.
- `GET /api/auth/login-history` returned JPA entities (with the owning `User`); it now returns a DTO.
- Refresh-token errors no longer echo the raw token into logs and responses.
- Refresh tokens are 256-bit random values and only their SHA-256 hash is stored, so a database leak
  cannot be replayed as live sessions.
- Access tokens are accepted only in the `Authorization` header (no `?token=` query parameter, which
  leaked into access/proxy logs); the notification stream now uses a fetch-based SSE client.
- No signing keys are committed anymore: the publicly known development JWT default was replaced by a
  random per-start key (set `SPRING_JWT_SECRET` to keep tokens across restarts), and tests generate
  their keys at runtime.
- SECURITY.md pointed at an unroutable `.local` mailbox; reports now go through GitHub private
  vulnerability reporting.

### Fixed
- The REST AI Coach (chat, greeting, weekly review, reminders) registered **no tools**: the varargs
  array was passed as a single tool object. Identity saving, habit creation and mood logging via chat
  now work.
- AI tools failed with "user is not authenticated" when AgentScope ran them off the request thread.
  Agents are now bound to their user explicitly.
- REST AI calls use non-streaming responses. Some OpenAI-compatible providers emit streamed tool-call
  deltas with empty names and arguments, which silently dropped tool calls.
- The weekly review passed `true` instead of the coach tools, so `present_weekly_review` was unavailable.
- `GET /api/users/stats/advanced` returned 500 for users with completions (lazy loading outside a
  transaction).
- Unknown routes, unsupported methods and malformed JSON now return 404/405/400 instead of 500.
- docker-compose set `AGENTSCOPE_BASE_URL`, but the app reads `AGENTSCOPE_MODEL_BASE_URL`.
- The AI proxy setting now applies only to model HTTP calls and to both coach paths, instead of
  mutating JVM-wide system properties on every request.
- **Reloading any page logged the user out**: concurrent refresh calls (React StrictMode, several tabs,
  parallel 401s) reused a rotated refresh token and failed with a 500. Rotation is now race-safe
  (the loser gets 401) and the frontend shares a single in-flight refresh.
- AI Coach (streaming): tools failed with "not authenticated", raw tool calls/results were rendered as
  JSON bubbles, the activity timeline stayed on "Connecting…", the conversation was wiped on every
  token refresh and not restored after a reload, and quick replies in ```` ```replies ```` fences were
  shown as raw arrays. The floating chat crashed the run on tool-only messages and did not refresh the
  dashboard after the coach changed data.
- Weekly review cards were saved with generic English highlights instead of the card the coach
  presented; replies now follow the UI language (`Accept-Language`), and an English request on a
  Chinese host no longer gets Chinese messages (`fallback-to-system-locale: false`).
- The full AI Coach page was unreachable from the UI (no navigation link) and had no way back; it
  also lacked dark-mode styles, showed duplicate greetings, and squeezed the chat under an always-open
  review history.
- Analytics: the 30-day series omitted days without completions, so charts interpolated over misses.
- Memory and weekly-review date labels used the server's locale ("10月 03" for English users); they
  now follow the request language. The analytics quote rendered with doubled quotation marks, and the
  dark-mode heatmap levels were nearly invisible.
- Identity header duplicated "I am" ("我是 I am a…"); pages kept the previous page's scroll position;
  pressing Enter to confirm an IME candidate sent half-typed Chinese text; notification toasts and the
  settings language hint were untranslated; registration used a blocking `alert()` and forced a second
  login; Panic Mode leaked timers and could not be closed with Escape.

### Changed
- Spring Boot 3.2.2 → 3.5.16, jjwt 0.11.5 → 0.13.0, springdoc 2.3.0 → 2.8.17,
  AgentScope 1.0.11 → 1.0.12 (final 1.x; see `docs/agentscope-2-migration.md` for the 2.x plan),
  maven-enforcer-plugin 3.5.0 → 3.6.2.
- AI model settings are bound once into `AiModelProperties` and built by a shared `ChatModelFactory`,
  replacing duplicated `@Value` fields.
- The AG-UI starter's own CORS handling is disabled; `SecurityConfig` is the single source of CORS policy.
- The backend container sizes its heap from the container memory limit and runs the JVM as PID 1 for
  graceful shutdown.

### Added
- README screenshots (dashboard, AI Coach, weekly review, habits, analytics, Panic Mode, dark mode).
- Flyway migrations (`V1__init.sql`, `V2__index_foreign_keys.sql`) with tests that run them on a real
  PostgreSQL 15 (embedded, no Docker needed), validate them against the JPA entities, and cover the
  baseline upgrade path for pre-Flyway databases.
- Release automation (`.github/workflows/release.yml`, `RELEASING.md`): tag-triggered build, tests,
  GitHub Release with jar/frontend bundle/`SHA256SUMS`, multi-arch images on GHCR, and build
  provenance attestations.
- Version and build time at `/actuator/info`; app version and release-notes link in Settings;
  reproducible build timestamps.
- Maven Wrapper (`backend/mvnw`), so contributors don't need a local Maven install.
- JaCoCo coverage reports (`./mvnw verify` → `target/site/jacoco/`), uploaded as a CI artifact.
- CI runs for the `development` branch, uploads Surefire reports on failure and builds both Docker images.
- CodeQL scanning for Java and TypeScript.
- Grouped Dependabot updates, plus Dockerfile base-image updates.
- Regression tests for every fix above.

[Unreleased]: https://github.com/inwardflow/atomic-habit/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/inwardflow/atomic-habit/releases/tag/v0.1.0
