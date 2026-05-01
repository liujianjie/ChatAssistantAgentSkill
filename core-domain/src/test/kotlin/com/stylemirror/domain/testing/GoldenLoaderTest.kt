package com.stylemirror.domain.testing

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe

/**
 * Smoke + invariant tests for the golden-fixture corpus.
 *
 * Two responsibilities:
 *  1. Verify [GoldenLoader] can parse every fixture into the typed domain
 *     model — this is the test the plan calls for in T03.
 *  2. Backstop the privacy red line: regex-scan every fixture for the obvious
 *     leak shapes (phone numbers, ID-card runs, emails). Authoring discipline
 *     is the primary defence; this test catches honest mistakes.
 */
class GoldenLoaderTest : StringSpec({

    // —— SMOKE ——

    "loadAll returns at least the seven required fixtures" {
        val all = GoldenLoader.loadAll()
        all shouldHaveAtLeastSize MIN_FIXTURE_COUNT
    }

    "every fixture has at least 30 messages" {
        GoldenLoader.loadAll().forEach { fx ->
            withClue("${fx.id}: only ${fx.messages.size} messages") {
                (fx.messages.size >= MIN_MESSAGES) shouldBe true
            }
        }
    }

    "every fixture has unique message ids" {
        GoldenLoader.loadAll().forEach { fx ->
            val ids = fx.messages.map { it.id.value }
            withClue(fx.id) { ids.toSet().size shouldBe ids.size }
        }
    }

    "every fixture has chronological timestamps" {
        GoldenLoader.loadAll().forEach { fx ->
            val expected = fx.messages.sortedBy { it.sentAt }.map { it.sentAt }
            withClue("${fx.id}: messages not in chronological order") {
                fx.messages.map { it.sentAt } shouldBe expected
            }
        }
    }

    "every fixture surfaces both my and their messages" {
        GoldenLoader.loadAll().forEach { fx ->
            withClue("${fx.id}: missing my messages") {
                fx.context.myMessages.isNotEmpty() shouldBe true
            }
            withClue("${fx.id}: missing their messages") {
                fx.context.theirMessages.isNotEmpty() shouldBe true
            }
        }
    }

    "load(id) round-trips with the source file name" {
        val baseline = GoldenLoader.load("01-1on1-baseline")
        baseline.id shouldBe "01-1on1-baseline"
        baseline.sourceFile shouldBe "01-1on1-baseline.yml"
    }

    "load throws on unknown id" {
        runCatching { GoldenLoader.load("does-not-exist") }
            .isFailure shouldBe true
    }

    // —— PRIVACY RED LINE BACKSTOP ——

    "fixtures contain no obvious PII (mobile / id-card / email)" {
        GoldenLoader.loadAll().forEach { fx ->
            fx.messages.forEach { m ->
                assertNoPii("${fx.id}/${m.id.value}", m.content)
            }
            // Also scan top-level fields, since 'description' / aliases / cues
            // are written by humans and could leak.
            assertNoPii("${fx.id}/description", fx.description)
            fx.myAliases.forEach { assertNoPii("${fx.id}/my_aliases", it) }
            fx.expectedStyleCues.forEach { assertNoPii("${fx.id}/cue", it) }
        }
    }
}) {
    companion object {
        private const val MIN_FIXTURE_COUNT = 7
        private const val MIN_MESSAGES = 30

        // Mainland China mobile: starts with 1, second digit 3–9, then 9 more digits.
        // Negative-lookaround digit boundaries so longer digit runs don't accidentally
        // match a substring (those get caught by ID_CARD_RUN instead).
        private val CN_MOBILE = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")

        // Any 17-digit run with a final digit-or-X. Coarse on purpose — the goal
        // is to catch accidental ID-card / bank-card sized runs, not to validate.
        private val ID_CARD_RUN = Regex("""(?<!\d)\d{17}[\dXx](?!\d)""")

        // RFC-5322-ish; any host on any TLD.
        private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

        private fun assertNoPii(
            locator: String,
            text: String,
        ) {
            withClue("$locator: contains 11-digit mobile-shaped run") {
                CN_MOBILE.containsMatchIn(text) shouldBe false
            }
            withClue("$locator: contains 18-char ID-card-shaped run") {
                ID_CARD_RUN.containsMatchIn(text) shouldBe false
            }
            withClue("$locator: contains email-shaped string") {
                EMAIL.containsMatchIn(text) shouldBe false
            }
        }
    }
}
