package com.stylemirror.feature.overlay.service

import com.stylemirror.domain.conversation.Message
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

private const val SCREEN_W = 1080
private val FIXED_NOW: () -> Instant = { Instant.parse("2026-05-31T10:00:00Z") }

private fun textNode(
    text: String,
    bounds: BoundsRect,
    className: String = "android.widget.TextView",
    viewId: String? = null,
): NodeView =
    NodeView(
        viewIdResourceName = viewId,
        className = className,
        text = text,
        boundsInScreen = bounds,
        children = emptyList(),
    )

private fun textNode(
    text: String,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
): NodeView = textNode(text, BoundsRect(left, top, right, bottom))

private fun root(children: List<NodeView>): NodeView =
    NodeView(
        viewIdResourceName = "root",
        className = "android.widget.FrameLayout",
        text = null,
        boundsInScreen = BoundsRect(0, 0, SCREEN_W, 1920),
        children = children,
    )

class SoulNodeMatchersTest : StringSpec({

    "empty tree returns null" {
        val ctx = SoulNodeMatchers.parse(root(emptyList()), SCREEN_W, FIXED_NOW)
        ctx.shouldBeNull()
    }

    "tree with only buttons / inputs returns null" {
        val tree =
            root(
                listOf(
                    textNode("发送", BoundsRect(800, 1800, 1000, 1880), className = "android.widget.Button"),
                    textNode("输入消息…", BoundsRect(100, 1800, 700, 1880), className = "android.widget.EditText"),
                    textNode("img", BoundsRect(100, 1700, 200, 1780), className = "android.widget.ImageView"),
                ),
            )
        SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldBeNull()
    }

    "right-side text becomes Mine, left-side becomes Theirs" {
        val tree =
            root(
                listOf(
                    textNode("你今晚有空吗", 60, 200, 480, 280),
                    textNode("有的", 700, 320, 900, 380),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages shouldHaveSize 2
        ctx.theirMessages.map { it.content } shouldBe listOf("你今晚有空吗")
        ctx.myMessages.map { it.content } shouldBe listOf("有的")
    }

    "messages are ordered top-down (older first)" {
        val tree =
            root(
                listOf(
                    textNode("第三条 Mine", 700, 600, 900, 660),
                    textNode("第二条 Theirs", 60, 400, 480, 460),
                    textNode("第一条 Theirs", 60, 200, 480, 260),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages.map { it.content } shouldBe listOf(
            "第一条 Theirs",
            "第二条 Theirs",
            "第三条 Mine",
        )
    }

    "synthesized sentAt is monotonically non-decreasing" {
        val tree =
            root(
                listOf(
                    textNode("a", 60, 100, 480, 160),
                    textNode("b", 60, 200, 480, 260),
                    textNode("c", 700, 300, 900, 360),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        val instants = ctx.messages.map { it.sentAt.toEpochMilli() }
        instants shouldBe instants.sorted()
    }

    "centered text (system notice) is dropped" {
        val tree =
            root(
                listOf(
                    textNode("张三 已撤回一条消息", 200, 400, 880, 460),
                    textNode("早", 700, 500, 900, 560),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages.map { it.content } shouldBe listOf("早")
    }

    "ambiguous-midline node (within tolerance) is dropped" {
        val tolerance = (SCREEN_W / 2 * 0.06).toInt()
        // place a node centered within tolerance of midline
        val midline = SCREEN_W / 2
        val width = 100
        val left = midline - tolerance / 2 - width / 2
        val right = left + width
        val tree =
            root(
                listOf(
                    textNode("ambiguous", left, 100, right, 160),
                    textNode("ok mine", 700, 200, 900, 260),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages shouldHaveSize 1
        ctx.messages.first().content shouldBe "ok mine"
    }

    "blank or whitespace-only text is dropped" {
        val tree =
            root(
                listOf(
                    textNode("   ", 60, 100, 480, 160),
                    textNode("", 700, 200, 900, 260),
                    textNode("real", 700, 300, 900, 360),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages.map { it.content } shouldBe listOf("real")
    }

    "zero-area bounds are dropped" {
        val tree =
            root(
                listOf(
                    textNode("offscreen", 0, 0, 0, 0),
                    textNode("real", 700, 300, 900, 360),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages.map { it.content } shouldBe listOf("real")
    }

    "deeply nested text is still found" {
        val deepLeaf = textNode("nested mine", 700, 100, 900, 160)
        val intermediate =
            NodeView(
                viewIdResourceName = null,
                className = "android.widget.LinearLayout",
                text = null,
                boundsInScreen = BoundsRect(0, 0, SCREEN_W, 1920),
                children = listOf(deepLeaf),
            )
        val tree =
            NodeView(
                viewIdResourceName = "list",
                className = "androidx.recyclerview.widget.RecyclerView",
                text = null,
                boundsInScreen = BoundsRect(0, 0, SCREEN_W, 1920),
                children = listOf(intermediate),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages.shouldHaveSize(1)
        ctx.messages.first() shouldBe Message.Mine(
            id = ctx.messages.first().id,
            content = "nested mine",
            sentAt = ctx.messages.first().sentAt,
        )
    }

    "screenWidth <= 0 returns null" {
        val tree = root(listOf(textNode("hi", 700, 100, 900, 160)))
        SoulNodeMatchers.parse(tree, screenWidth = 0, now = FIXED_NOW).shouldBeNull()
        SoulNodeMatchers.parse(tree, screenWidth = -100, now = FIXED_NOW).shouldBeNull()
    }

    "Theirs.displayName placeholder is constant" {
        val tree = root(listOf(textNode("hi", 60, 100, 480, 160)))
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        val theirs = ctx.theirMessages.single()
        theirs.displayName shouldBe "对方"
    }

    "PartnerId is the well-known overlay constant" {
        val tree = root(listOf(textNode("hi", 60, 100, 480, 160)))
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.partnerId.value shouldBe SoulNodeMatchers.SOUL_OVERLAY_PARTNER_ID
    }

    "no theirs messages stays clean (privacy red line: never bucket unknown to either side)" {
        val tree = root(emptyList())
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW)
        ctx.shouldBeNull()
    }

    "ImageView with text is excluded" {
        val tree =
            root(
                listOf(
                    textNode(
                        "emoji_alt_text",
                        BoundsRect(60, 100, 480, 160),
                        className = "android.widget.ImageView",
                    ),
                    textNode("real", 700, 300, 900, 360),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.messages.shouldHaveSize(1)
        ctx.messages.first().content shouldBe "real"
    }

    "myMessages and theirMessages partition the result" {
        val tree =
            root(
                listOf(
                    textNode("their 1", 60, 100, 480, 160),
                    textNode("mine 1", 700, 200, 900, 260),
                    textNode("their 2", 60, 300, 480, 360),
                    textNode("mine 2", 700, 400, 900, 460),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        (ctx.myMessages.size + ctx.theirMessages.size) shouldBe ctx.messages.size
        ctx.myMessages.map { it.content } shouldBe listOf("mine 1", "mine 2")
        ctx.theirMessages.map { it.content } shouldBe listOf("their 1", "their 2")
    }

    "all-mine returns no theirs but is still valid" {
        val tree =
            root(
                listOf(
                    textNode("自言", 700, 100, 900, 160),
                    textNode("自语", 700, 200, 900, 260),
                ),
            )
        val ctx = SoulNodeMatchers.parse(tree, SCREEN_W, FIXED_NOW).shouldNotBeNull()
        ctx.theirMessages.shouldBeEmpty()
        ctx.myMessages shouldHaveSize 2
    }
})
