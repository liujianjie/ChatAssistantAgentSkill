package com.stylemirror.feature.realtime.input

import app.cash.turbine.test
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.PartnerId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val PARTNER = PartnerId("p-test")

// Frozen clock so synthetic timestamps are reproducible without relying on
// virtual time tricks for the parser itself (parsing is not suspending).
private val FIXED_INSTANT: Instant = Instant.parse("2026-05-01T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)

@OptIn(ExperimentalCoroutinesApi::class)
class PasteInputTest : StringSpec({

    "empty paste yields a single empty ConversationContext emission" {
        runTest(UnconfinedTestDispatcher()) {
            val event = PasteEvent(rawText = "", partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.partnerId shouldBe PARTNER
                ctx.messages.shouldHaveSize(0)
                awaitComplete()
            }
        }
    }

    "blank-only paste (whitespace + newlines) is treated as empty" {
        runTest(UnconfinedTestDispatcher()) {
            val event = PasteEvent(rawText = "\n   \n\r\n   \n", partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                awaitItem().messages.shouldHaveSize(0)
                awaitComplete()
            }
        }
    }

    "single bare line defaults to Mine" {
        runTest(UnconfinedTestDispatcher()) {
            val event = PasteEvent(rawText = "hello world", partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(1)
                val msg = ctx.messages.first()
                msg.shouldBeInstanceOf<Message.Mine>()
                msg.content shouldBe "hello world"
                msg.sentAt shouldBe FIXED_INSTANT
                awaitComplete()
            }
        }
    }

    "multi-line mix: 我:/小明: prefixes split, bare lines inherit speaker" {
        runTest(UnconfinedTestDispatcher()) {
            val raw =
                """
                我：今天去看展啦
                continued thought of mine
                小明: 哪个展？
                忘了名字
                我: 周六见
                """.trimIndent()
            val event = PasteEvent(rawText = raw, partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(5)

                ctx.messages[0].shouldBeInstanceOf<Message.Mine>().also {
                    it.content shouldBe "今天去看展啦"
                }
                ctx.messages[1].shouldBeInstanceOf<Message.Mine>().also {
                    // bare continuation inherits the previous speaker
                    it.content shouldBe "continued thought of mine"
                }
                ctx.messages[2].shouldBeInstanceOf<Message.Theirs>().also {
                    it.content shouldBe "哪个展？"
                    it.displayName shouldBe "小明"
                }
                ctx.messages[3].shouldBeInstanceOf<Message.Theirs>().also {
                    it.content shouldBe "忘了名字"
                    it.displayName shouldBe "对方"
                }
                ctx.messages[4].shouldBeInstanceOf<Message.Mine>().also {
                    it.content shouldBe "周六见"
                }
                // Synthetic timestamps advance one second per line.
                ctx.messages[4].sentAt shouldBe FIXED_INSTANT.plusSeconds(4)
                awaitComplete()
            }
        }
    }

    "long paste (≥ 200 lines) parses in one emission with all lines kept" {
        runTest(UnconfinedTestDispatcher()) {
            val lineCount = 220
            val raw =
                buildString {
                    repeat(lineCount) { i ->
                        val who = if (i % 2 == 0) "我" else "对方"
                        append(who).append("：").append("line-").append(i).append('\n')
                    }
                }
            val event = PasteEvent(rawText = raw, partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(lineCount)
                ctx.myMessages.size shouldBe lineCount / 2
                ctx.theirMessages.size shouldBe lineCount / 2
                awaitComplete()
            }
        }
    }

    "emoji + Chinese/English code-switching from fixture-02 snippet survives intact" {
        runTest(UnconfinedTestDispatcher()) {
            val raw = readPasteResource("paste/02-emoji-snippet.txt")
            val event = PasteEvent(rawText = raw, partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(5)
                ctx.messages[0].content shouldBe "晚上好呀😄"
                ctx.messages[2].content shouldBe "今天看到一只猫超可爱🐱😍"
                ctx.messages[4].content shouldBe "哈哈也是😂晚安喵🌙"

                // Speaker alternation must follow the prefixes, not the emoji.
                ctx.messages[0].shouldBeInstanceOf<Message.Mine>()
                ctx.messages[1].shouldBeInstanceOf<Message.Theirs>().displayName shouldBe "阿杰"
                awaitComplete()
            }
        }
    }

    "myAliases override the default Chinese-only Mine heuristic" {
        runTest(UnconfinedTestDispatcher()) {
            val raw =
                """
                Lily: foo
                闫子: bar
                Lily: baz
                """.trimIndent()
            val event =
                PasteEvent(
                    rawText = raw,
                    partnerId = PARTNER,
                    myAliases = setOf("Lily"),
                )
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(3)
                ctx.messages[0].shouldBeInstanceOf<Message.Mine>()
                ctx.messages[1].shouldBeInstanceOf<Message.Theirs>().displayName shouldBe "闫子"
                ctx.messages[2].shouldBeInstanceOf<Message.Mine>()
                awaitComplete()
            }
        }
    }

    "explicit overrides win over heuristic classification" {
        runTest(UnconfinedTestDispatcher()) {
            val raw =
                """
                我：第一句
                小明: 第二句
                小明: 第三句
                """.trimIndent()
            val event =
                PasteEvent(
                    rawText = raw,
                    partnerId = PARTNER,
                    // Flip "小明: 第二句" (line index 1, 0-based among non-empty lines)
                    // from Theirs back to Mine — e.g. the user mistyped a label.
                    overrides = mapOf(1 to Speaker.ME),
                )
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(3)
                ctx.messages[0].shouldBeInstanceOf<Message.Mine>()
                withClue("override should flip line 1 to Mine despite 小明 prefix") {
                    ctx.messages[1].shouldBeInstanceOf<Message.Mine>()
                    ctx.messages[1].content shouldBe "第二句"
                }
                ctx.messages[2].shouldBeInstanceOf<Message.Theirs>()
                awaitComplete()
            }
        }
    }

    "ASCII colon and full-width 全角 colon are both recognised as separators" {
        runTest(UnconfinedTestDispatcher()) {
            val raw =
                """
                小明: ascii colon
                小红：fullwidth colon
                """.trimIndent()
            val event = PasteEvent(rawText = raw, partnerId = PARTNER)
            val adapter = PasteInput(flowOf(event), clock = FIXED_CLOCK)

            adapter.receive().test {
                val ctx = awaitItem()
                ctx.messages.shouldHaveSize(2)
                ctx.messages[0].shouldBeInstanceOf<Message.Theirs>().also {
                    it.displayName shouldBe "小明"
                    it.content shouldBe "ascii colon"
                }
                ctx.messages[1].shouldBeInstanceOf<Message.Theirs>().also {
                    it.displayName shouldBe "小红"
                    it.content shouldBe "fullwidth colon"
                }
                awaitComplete()
            }
        }
    }

    "two paste events become two ConversationContext emissions with stable ids" {
        runTest(UnconfinedTestDispatcher()) {
            val first = PasteEvent(rawText = "我：hi", partnerId = PARTNER)
            val second = PasteEvent(rawText = "我：hello again", partnerId = PARTNER)
            val adapter = PasteInput(flowOf(first, second), clock = FIXED_CLOCK)

            adapter.receive().test {
                val a = awaitItem()
                val b = awaitItem()
                a.messages.first().id.value shouldBe "paste-0-0"
                b.messages.first().id.value shouldBe "paste-1-0"
                awaitComplete()
            }
        }
    }
})

private fun readPasteResource(path: String): String {
    val classLoader =
        checkNotNull(PasteInputTest::class.java.classLoader) {
            "PasteInputTest has no classloader (test runtime misconfigured)"
        }
    val stream =
        checkNotNull(classLoader.getResourceAsStream(path)) {
            "test resource '$path' not on classpath"
        }
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
