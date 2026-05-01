# ADR-0002: LLMProvider Strategy

- **Status**: Accepted
- **Date**: 2026-05-01
- **Task**: T05
- **Supersedes / Superseded by**: —

## Context

SPEC §1.4 / §1.6 require:
- ≤3 s P95 for 3 candidates on the realtime path
- Default to DeepSeek (cheapest credible Chinese-aware model), keep Claude
  and Qwen as runtime-pluggable alternates
- API keys stored encrypted, never in source / settings / logs

We need a provider abstraction that:
1. Lets the candidate generator (T07) call into "the LLM" without knowing
   which vendor it is.
2. Doesn't bleed vendor-specific request / response shapes into core-domain.
3. Maps every transport failure to a typed [DomainError] so
   ViewModels can `when` exhaustively without `try`/`catch`.
4. Allows live-API tests against the default vendor without touching CI.

## Decision

### One interface, narrow surface

```kotlin
interface LLMProvider {
    suspend fun generateCandidates(
        prompt: String,
        maxTokens: Int = 256,
        n: Int = 3,
    ): Outcome<List<Candidate>, DomainError>
}
```

The whole prompt is a single `String` — the candidate generator owns prompt
assembly. Vendor APIs that want role-segmented messages (DeepSeek's
OpenAI-compat chat-completions) wrap the prompt in a single user-role
message at the implementation boundary.

### Wide error channel (`DomainError`, not `DomainError.LlmFailure`)

Stub providers (Claude, Qwen) return `DomainError.NotImplemented`, which is
not a subtype of `LlmFailure`. Widening the error type lets stubs honour
the contract without inventing a synthetic `NotImplemented` reason on
`LlmFailureReason`. Callers stay exhaustive via the sealed `DomainError`.

### Production = DeepSeek with a SecureKeyStore-bound bearer token

`DeepSeekProvider` reads its API key from `SecureKeyStore` on every call
(no in-memory caching) so:
- key rotation takes effect immediately,
- the secret never sits in heap longer than the call's stack frame.

HTTP failure mapping is opinionated:
- 401 / 403 → `AUTH`
- 429       → `RATE_LIMITED`
- 5xx       → `SERVER_ERROR`
- read/write timeout → `TIMEOUT`
- other IOException  → `SERVER_ERROR` with `cause`
- empty `choices` array → `INVALID_RESPONSE`
- key missing in store → `KEY_MISSING` (no HTTP attempt)

### Stubs ship now, not later

Claude / Qwen stubs return `Err(DomainError.NotImplemented)`. They exist
because:
- DI graphs and provider-picker UI surface a stable type before the real
  implementation lands
- Adding the real impl later doesn't churn caller types
- Type-system answers "what happens if a user picks Claude pre-launch?"
  better than docs do

### Live-API tests live in a separate Gradle source set

`infra-llm` carries an `integrationTest` source set whose only test
(`DeepSeekIntegrationTest`) hits the real endpoint. The Gradle task is
gated by `onlyIf { STYLEMIRROR_DEEPSEEK_KEY env-var present }`, and the
test itself uses `@EnabledIfEnvironmentVariable` as defence-in-depth so
running it directly from an IDE without the key is also a no-op.

CI runs `./gradlew check`, which never invokes the `integrationTest`
task. Live coverage is the developer's responsibility before merging
provider changes.

## Alternatives considered

### `Outcome<List<Candidate>, DomainError.LlmFailure>` (narrow error)

Rejected. Adding a `NOT_IMPLEMENTED` value to `LlmFailureReason` to
accommodate stub providers conflates "the LLM produced an unusable
result" with "this provider isn't built yet" — two failure modes the UI
should treat differently.

### Vendor-specific request types (`DeepSeekPrompt`, `ClaudeMessages`)

Rejected. Forces every consumer to know which provider is wired; defeats
the abstraction. The vendor split is solved by keeping vendor-shaped
DTOs `internal` to `infra-llm.<vendor>.*` packages.

### Exception-throwing providers

Rejected. `Outcome` is the project's universal result discipline (see
`Outcome.kt`). Throwing collapses the typed error taxonomy into untyped
`Throwable` and breaks exhaustive `when`.

### Live-API tests under `src/test/` with `@Tag("integration")`

Rejected because `./gradlew check` runs every JUnit5 task by default,
and excluding a tag still requires every contributor's CI config to
respect it. A dedicated source set + Gradle task makes the live-vs-fake
split an architectural property, not a configuration knob.

## Consequences

- `LLMProvider` is the only contact point between the candidate
  generator and any external model. Adding a fourth vendor is a
  copy-paste of the DeepSeek shape with vendor-specific DTOs.
- Live-API regressions are caught only when the developer remembers to
  run `:infra-llm:integrationTest` before pushing. The
  `RedactingHttpLogger` from T04 keeps the API key out of logs even
  when a test failure dumps the request/response trail.
- The wide error channel means `LlmFailureReason` does not need to
  change as we add stub providers. Real Claude/Qwen impls (P1) will
  reuse the existing reasons.
