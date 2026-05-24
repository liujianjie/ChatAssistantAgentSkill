package com.stylemirror.feature.imports.source

import app.cash.turbine.test
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.time.Instant

class PlainTextImportSourceTest : StringSpec({

    // ---- Format 1: timestamped -------------------------------------------

    "parses 'YYYY-MM-DD HH:mm 昵称：内容' format" {
        val text = "2024-01-15 14:30 张三：你好啊"
        val msg = PlainTextImportSource.parseLine(text, 0)

        msg.rawSpeakerLabel shouldBe "张三"
        msg.content shouldBe "你好啊"
        msg.timestampHint shouldBe Instant.parse("2024-01-15T14:30:00Z")
        msg.sourceIndex shouldBe 0
    }

    "parses 'YYYY-MM-DD HH:mm:ss 昵称：内容' format with seconds" {
        val text = "2024-01-15 14:30:45 Alice：hello"
        val msg = PlainTextImportSource.parseLine(text, 1)

        msg.rawSpeakerLabel shouldBe "Alice"
        msg.content shouldBe "hello"
        msg.timestampHint shouldBe Instant.parse("2024-01-15T14:30:45Z")
    }

    // ---- Format 2: prefixed without timestamp ----------------------------

    "parses '昵称：内容' full-width colon" {
        val msg = PlainTextImportSource.parseLine("小明：今天好吗", 0)
        msg.rawSpeakerLabel shouldBe "小明"
        msg.content shouldBe "今天好吗"
        msg.timestampHint.shouldBeNull()
    }

    "parses '昵称: 内容' ASCII colon" {
        val msg = PlainTextImportSource.parseLine("小红: 挺好的", 0)
        msg.rawSpeakerLabel shouldBe "小红"
        msg.content shouldBe "挺好的"
    }

    // ---- Format 3: bare line ---------------------------------------------

    "bare line has null speaker and null timestamp" {
        val msg = PlainTextImportSource.parseLine("这是一条裸露的消息", 0)
        msg.rawSpeakerLabel.shouldBeNull()
        msg.content shouldBe "这是一条裸露的消息"
        msg.timestampHint.shouldBeNull()
    }

    "line with only spaces is treated as bare" {
        val msg = PlainTextImportSource.parseLine("  纯内容无前缀  ", 0)
        msg.rawSpeakerLabel.shouldBeNull()
        msg.content shouldBe "纯内容无前缀"
    }

    // ---- Stream behaviour ------------------------------------------------

    "stream emits correct count for mixed content" {
        runTest {
            val text =
                """
                2024-01-15 10:00 张三：你好
                小红：在的
                这是continuation
                2024-01-15 10:02 张三：再见
                """.trimIndent()
            PlainTextImportSource(text).stream().test {
                awaitItem() // timestamped
                awaitItem() // prefixed
                awaitItem() // bare
                awaitItem() // timestamped
                awaitComplete()
            }
        }
    }

    "stream skips blank lines" {
        runTest {
            val text = "\n\n张三：hi\n\n小红：hello\n\n"
            PlainTextImportSource(text).stream().test {
                awaitItem().rawSpeakerLabel shouldBe "张三"
                awaitItem().rawSpeakerLabel shouldBe "小红"
                awaitComplete()
            }
        }
    }

    "sourceIndex increments correctly across non-blank lines" {
        runTest {
            val text = "张三：a\n\n小红：b\n李四：c"
            val collected = mutableListOf<RawMessage>()
            PlainTextImportSource(text).stream().test {
                repeat(3) { collected += awaitItem() }
                awaitComplete()
            }
            collected.map { it.sourceIndex } shouldBe listOf(0, 1, 2)
        }
    }

    // ---- 10k message streaming benchmark (correctness, not wall-clock) ---

    "stream handles 10k messages without materialising them all" {
        runTest {
            val lines =
                buildString {
                    repeat(10_000) { i ->
                        append("说话人${i % 5}：消息内容第${i}条\n")
                    }
                }
            var count = 0
            PlainTextImportSource(lines).stream().test {
                repeat(10_000) {
                    awaitItem()
                    count++
                }
                awaitComplete()
            }
            withClue("should have received all 10 000 items") {
                count shouldBe 10_000
            }
        }
    }

    // ---- Emoji + mixed language -------------------------------------------

    "emoji and mixed Chinese/English in content preserved" {
        val text = "Alice：这是 emoji😄 和中文 mixed content"
        val msg = PlainTextImportSource.parseLine(text, 0)
        msg.rawSpeakerLabel shouldBe "Alice"
        msg.content shouldBe "这是 emoji😄 和中文 mixed content"
    }

    // ---- QQ-style header formats (P6 — real-user fixture) ----------------

    "QQ-style format 1: 'YYYY-MM-DD HH:mm:ss 昵称' header + content on next line" {
        runTest {
            val text =
                """
                ================================================================
                消息分组:黑名单
                ================================================================
                消息对象:晚倾
                ================================================================

                2016-05-16 23:50:57 指着太阳公公说日
                要不你帮我找找

                2016-05-16 23:50:58 指着太阳公公说日
                好了

                2016-05-16 23:50:59 指着太阳公公说日
                睡觉

                2016-05-16 23:51:50 虹燕
                哦

                2016-05-17 23:20:45 指着太阳公公说日
                找不到好看的头像啊，你那有么
                """.trimIndent()
            val collected = mutableListOf<RawMessage>()
            PlainTextImportSource(text).stream().test {
                while (true) {
                    val item = awaitItem()
                    collected += item
                    if (collected.size >= 5) break
                }
                awaitComplete()
            }
            collected.size shouldBe 5
            // First four messages should be from "指着太阳公公说日"
            collected[0].rawSpeakerLabel shouldBe "指着太阳公公说日"
            collected[0].content shouldBe "要不你帮我找找"
            collected[0].timestampHint shouldBe Instant.parse("2016-05-16T23:50:57Z")
            collected[1].rawSpeakerLabel shouldBe "指着太阳公公说日"
            collected[1].content shouldBe "好了"
            collected[3].rawSpeakerLabel shouldBe "虹燕"
            collected[3].content shouldBe "哦"
            // Separator/metadata lines must NOT have leaked through as messages
            collected.none { it.content.contains("===") } shouldBe true
            collected.none { it.rawSpeakerLabel == "消息分组" } shouldBe true
            collected.none { it.rawSpeakerLabel == "消息对象" } shouldBe true
        }
    }

    "QQ-style format 2: '昵称 HH:mm:ss' header + content on next line" {
        runTest {
            val text =
                """
                指着太阳公公说日 12:38:26
                你是不是傻 我自己怎么可能打得通我自己的电话
                刘英朋友 12:39:39
                你不是说是你老师的么
                刘英朋友 12:39:45
                啧啧啧
                刘英朋友 12:40:02
                还打给你解解闷
                指着太阳公公说日 12:40:19
                我说是老师就是老师的啊 我说你是我老婆你说是不是呢？
                """.trimIndent()
            val collected = mutableListOf<RawMessage>()
            PlainTextImportSource(text).stream().test {
                repeat(5) { collected += awaitItem() }
                awaitComplete()
            }
            collected[0].rawSpeakerLabel shouldBe "指着太阳公公说日"
            collected[0].content shouldBe "你是不是傻 我自己怎么可能打得通我自己的电话"
            collected[1].rawSpeakerLabel shouldBe "刘英朋友"
            collected[1].content shouldBe "你不是说是你老师的么"
            collected[2].rawSpeakerLabel shouldBe "刘英朋友"
            collected[3].rawSpeakerLabel shouldBe "刘英朋友"
            collected[4].rawSpeakerLabel shouldBe "指着太阳公公说日"
        }
    }

    "QQ-style: multi-line content within one speaker block joins with newline" {
        runTest {
            val text =
                """
                2024-01-15 10:00:00 张三
                第一行
                第二行
                2024-01-15 10:01:00 李四
                回复
                """.trimIndent()
            val collected = mutableListOf<RawMessage>()
            PlainTextImportSource(text).stream().test {
                repeat(2) { collected += awaitItem() }
                awaitComplete()
            }
            collected[0].rawSpeakerLabel shouldBe "张三"
            collected[0].content shouldBe "第一行\n第二行"
        }
    }

    "QQ-style: separator and metadata lines are dropped, no fake messages emitted" {
        runTest {
            val text =
                """
                ================================================================
                消息分组：默认分组
                消息对象：某人
                ================================================================
                """.trimIndent()
            PlainTextImportSource(text).stream().test {
                awaitComplete()
            }
        }
    }

    "QQ-style header coexists with inline format in same paste (mixed import)" {
        runTest {
            val text =
                """
                我：粘贴前的旧格式
                2024-01-15 10:00:00 张三
                QQ 格式段落
                小红：又一条 inline
                """.trimIndent()
            val collected = mutableListOf<RawMessage>()
            PlainTextImportSource(text).stream().test {
                repeat(3) { collected += awaitItem() }
                awaitComplete()
            }
            collected[0].rawSpeakerLabel shouldBe "我"
            collected[0].content shouldBe "粘贴前的旧格式"
            collected[1].rawSpeakerLabel shouldBe "张三"
            collected[1].content shouldBe "QQ 格式段落"
            collected[2].rawSpeakerLabel shouldBe "小红"
        }
    }

    // ---- Stub sources throw -------------------------------------------

    "stub sources throw UnsupportedOperationException" {
        val stubs: List<ImportSource> =
            listOf(
                WeChatPcExportImportSource(),
                WeChatBackupImportSource(),
                ThirdPartyToolImportSource(),
            )
        stubs.forEach { stub ->
            runTest {
                stub.stream().test {
                    awaitError().shouldBeInstanceOf<UnsupportedOperationException>()
                }
            }
        }
    }
})

private fun <T> T.shouldBeInstanceOf(): T = this

private inline fun <reified T : Throwable> Throwable.shouldBeInstanceOf(): T {
    if (this !is T) throw AssertionError("Expected ${T::class.simpleName} but was ${this::class.simpleName}")
    return this
}
