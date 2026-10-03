# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- JWTs signed with an unknown key are now rejected as unauthenticated. Previously the
  `SignatureException` escaped the authentication filter.
- AI Coach tools no longer act on a model-supplied `email` argument. The user is resolved from
  server-side state only, so a prompt-injected address cannot read or modify another account.
- A valid token for a deleted user is treated as anonymous instead of causing a server error.
- The `prod` profile refuses to start without `SPRING_JWT_SECRET`, and weak (< 256-bit) secrets
  fail at startup instead of on first login.

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

### Changed
- Spring Boot 3.2.2 → 3.5.16, jjwt 0.11.5 → 0.13.0, springdoc 2.3.0 → 2.8.17,
  maven-enforcer-plugin 3.5.0 → 3.6.2.
- AI model settings are bound once into `AiModelProperties` and built by a shared `ChatModelFactory`,
  replacing duplicated `@Value` fields.
- The AG-UI starter's own CORS handling is disabled; `SecurityConfig` is the single source of CORS policy.
- The backend container sizes its heap from the container memory limit and runs the JVM as PID 1 for
  graceful shutdown.

### Added
- Maven Wrapper (`backend/mvnw`), so contributors don't need a local Maven install.
- JaCoCo coverage reports (`./mvnw verify` → `target/site/jacoco/`), uploaded as a CI artifact.
- CI runs for the `development` branch, uploads Surefire reports on failure and builds both Docker images.
- CodeQL scanning for Java and TypeScript.
- Grouped Dependabot updates, plus Dockerfile base-image updates.
- Regression tests for every fix above (57 backend tests in total).
