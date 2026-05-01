package com.stylemirror.infra.llm.deepseek

import com.stylemirror.domain.error.Outcome
import com.stylemirror.infra.llm.testing.InMemorySecureKeyStore
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Live-API smoke test for [DeepSeekProvider]. Hits the real DeepSeek endpoint
 * to verify our DTOs, auth wiring, and error mapping survive provider drift.
 *
 * Lives in the `integrationTest` source set so `./gradlew check` (run by CI)
 * never reaches the real network. Trigger locally with:
 *
 *     STYLEMIRROR_DEEPSEEK_KEY=sk-... ./gradlew :infra-llm:integrationTest
 *
 * [EnabledIfEnvironmentVariable] is a defence-in-depth back-stop in addition
 * to the Gradle task's `onlyIf` predicate — running the test class directly
 * (e.g. from an IDE) without the env var is also a no-op.
 */
@EnabledIfEnvironmentVariable(named = "STYLEMIRROR_DEEPSEEK_KEY", matches = ".+")
class DeepSeekIntegrationTest {
    @Test
    fun `live DeepSeek endpoint returns at least one non-blank candidate`() =
        runBlocking {
            val key = System.getenv("STYLEMIRROR_DEEPSEEK_KEY")
            val keyStore = InMemorySecureKeyStore(mapOf(DeepSeekProvider.DEFAULT_KEY_NAME to key))
            val provider = DeepSeekProvider.create(keyStore = keyStore)

            val outcome =
                provider.generateCandidates(
                    prompt = "Reply with exactly the word: pong",
                    maxTokens = 16,
                    n = 1,
                )

            check(outcome is Outcome.Ok) {
                "Expected Ok; got Err=${(outcome as Outcome.Err).error}"
            }
            outcome.value.shouldNotBeEmpty()
            outcome.value.first().text.shouldNotBeBlank()
        }
}
