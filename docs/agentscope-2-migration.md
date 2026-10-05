# AgentScope Java 2.x migration plan

Status: **planned for v0.2.0**. v0.1.0 ships on AgentScope `1.0.12`, the final 1.x release
(1.1.0 never left RC). AgentScope 2.0 went GA and is maintained on the `2.0.x` line.

Sources: [V1 migration guide](https://java.agentscope.io/v2/zh/docs/change-log),
[release notes](https://java.agentscope.io/v2/zh/docs/others/release-notes),
[AG-UI integration](https://java.agentscope.io/v2/zh/integration/protocol/agui).

## Why migrate

- **1.x is end-of-line**: no further fixes, including the 2.x fix for `JdkHttpTransport` SSE
  connections being cut by the absolute timeout.
- **Native per-user context**: AG-UI v2 resolves a `RuntimeContext(userId, sessionId)` per request
  through `AguiRuntimeContextResolver`, and tools can read it via `RuntimeContext`. This replaces our
  hand-built binding (`AguiThreadBindingFilter`, `TrackingThreadSessionManager.creatingThreadId()`,
  `AgentUserRegistry`) with a framework-supported mechanism.
- **Stateless agents + `AgentStateStore`**: conversations survive restarts and work across replicas,
  instead of living in an in-process `InMemoryMemory`.
- Model retry/fallback (`maxRetries`, `fallbackModel`), typed event stream (`streamEvents()`),
  middleware instead of hooks, built-in HITL/permissions.

Compatibility was checked for 2.0.3: classes target Java 17 (class file 61) and the starters only use
Spring Boot 3-compatible auto-configuration APIs, so no Java or Spring Boot upgrade is required.

## Required changes in this codebase

| Area | v1 usage here | v2 change |
| --- | --- | --- |
| Model | `io.agentscope.core.model.OpenAIChatModel` in `ChatModelFactory` | Provider moved out of core: add `agentscope-extensions-model-openai` (or `agentscope-openai-spring-boot-starter`), import `io.agentscope.extensions.model.openai.OpenAIChatModel`. Re-check `JdkHttpTransport` / `ProxyConfig` locations. |
| Agent memory | `ReActAgent.builder().memory(new InMemoryMemory())` in `AguiConfig` | `.memory(...)` removed: use `.stateStore(AgentStateStore)`; consider a JDBC/Redis store for multi-instance deployments. |
| User binding | thread-id filter + `AgentUserRegistry` + `Agent` tool parameter | Implement `AguiRuntimeContextResolver` from the authenticated principal; resolve the user in `CoachTools` from `RuntimeContext.getUserId()`; delete the custom binding classes. Keep the rule that model-supplied identifiers are never trusted. |
| AG-UI session manager | `TrackingThreadSessionManager extends ThreadSessionManager` | Re-evaluate against the v2 starter (agents are stateless; sessions are keyed by `RuntimeContext`). |
| Long-term memory | `CoachLongTermMemory implements LongTermMemory`, `LongTermMemoryMode.STATIC_CONTROL` | Deprecated for removal and being rewritten upstream. Keep it working on 2.0.x (still callable), then move retrieval into a middleware (`onSystemPrompt`/`onReasoning`) and recording to our own post-call step, independent of the upstream module. |
| Messages | `Msg.builder().role(USER).content(TextBlock...)` | Still valid; prefer `UserMessage`/`AssistantMessage`. Role/content combinations are validated at construction. |
| Streaming | REST path blocks on `agent.call(...)` | Unchanged; optionally adopt `streamEvents()` for REST streaming. |
| AG-UI events | Frontend maps `RUN_*`, `TEXT_MESSAGE_*`, `TOOL_CALL_*` | Same event names; sub-agent events now arrive as `CUSTOM` (`subagent.*`), which we don't use yet. |

## Plan

1. Branch `feat/agentscope-2`; bump `agentscope.version` to the latest 2.0.x and add the OpenAI
   model extension; fix compile errors (model import, `.memory()` -> `.stateStore()`).
2. Replace the user binding with `AguiRuntimeContextResolver` + `RuntimeContext`; port
   `CoachToolsTest` / `AguiThreadBindingFilterTest` to assert the same guarantee (a client cannot pick
   another user's context).
3. Keep `CoachLongTermMemory` on the deprecated API behind an interface; schedule the middleware
   rewrite separately.
4. Re-run the full suite plus the live smoke test (identity save, habit creation, mood logging,
   weekly review card, memory hits) against an OpenAI-compatible provider, in the coach page and the
   floating chat.
5. Remove the AgentScope major-version ignore from `.github/dependabot.yml`.
