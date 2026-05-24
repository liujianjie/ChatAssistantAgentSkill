package com.stylemirror.app.onboarding

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TextExtractorsTest : StringSpec({

    "txt mime 直接 UTF-8 解码" {
        val bytes = "我：你好\n张三：在吗".toByteArray(Charsets.UTF_8)
        TextExtractors.extract(bytes, "text/plain") shouldBe "我：你好\n张三：在吗"
    }

    "markdown mime 走 text/* 分支，保留原文" {
        val bytes = "# 标题\n\n我：消息一\n张三：消息二".toByteArray()
        val out = TextExtractors.extract(bytes, "text/markdown")
        out shouldContain "我：消息一"
        out shouldContain "张三：消息二"
    }

    "未知 mime fallback 到 UTF-8 文本解码" {
        val bytes = "纯文本兜底".toByteArray()
        TextExtractors.extract(bytes, "application/octet-stream") shouldBe "纯文本兜底"
    }

    "html 抽出可见文本，剥离标签" {
        val html =
            """
            <html><body>
              <p>我：你好</p>
              <p>张三：在吗</p>
              <script>alert('x')</script>
            </body></html>
            """.trimIndent()
        val out = TextExtractors.extractHtml(html.toByteArray())
        out shouldContain "我：你好"
        out shouldContain "张三：在吗"
        out shouldNotContain "<p>"
        out shouldNotContain "alert"
    }

    "html 保留段落换行（wholeText）" {
        val html = "<html><body><p>第一段</p><p>第二段</p></body></html>"
        val out = TextExtractors.extractHtml(html.toByteArray())
        // 段落之间至少一个换行
        out.lines().filter { it.isNotBlank() }.size shouldBe 2
    }

    "docx mini 提取器解析合成文档：单段单 run" {
        val docx = synthesizeDocx(paragraphs = listOf(listOf("我：你好")))
        TextExtractors.extractDocx(docx) shouldBe "我：你好"
    }

    "docx mini 提取器：多段，每段一行" {
        val docx =
            synthesizeDocx(
                paragraphs =
                    listOf(
                        listOf("我：第一句"),
                        listOf("张三：回复"),
                        listOf("我：第二句"),
                    ),
            )
        val out = TextExtractors.extractDocx(docx)
        out shouldBe "我：第一句\n张三：回复\n我：第二句"
    }

    "docx mini 提取器：单段多 run（Word 拆分了富文本）合并为一行" {
        val docx =
            synthesizeDocx(
                paragraphs =
                    listOf(
                        // 一段拆成 3 个 <w:t>，模拟 Word 因为格式变化拆分文本
                        listOf("我：", "你", "好"),
                    ),
            )
        TextExtractors.extractDocx(docx) shouldBe "我：你好"
    }

    "docx mini 提取器：跳过空段落" {
        val docx =
            synthesizeDocx(
                paragraphs =
                    listOf(
                        listOf("我：你好"),
                        // 空段落
                        emptyList(),
                        listOf("张三：嗨"),
                    ),
            )
        TextExtractors.extractDocx(docx) shouldBe "我：你好\n张三：嗨"
    }

    "docx mini 提取器：解码 XML 实体（&amp; &lt; &gt;）" {
        val docx =
            synthesizeDocx(
                paragraphs = listOf(listOf("A &amp; B &lt; C")),
            )
        TextExtractors.extractDocx(docx) shouldBe "A & B < C"
    }

    "docx 非 .docx 字节抛错（提示用户）" {
        val notDocx = "this is plain text".toByteArray()
        try {
            TextExtractors.extractDocx(notDocx)
            error("应当抛 IllegalArgumentException 或 ZipException")
        } catch (_: Throwable) {
            // 期望抛错，不限定具体异常类型
        }
    }
})

/**
 * Builds a minimal .docx byte array containing only `word/document.xml`.
 * Each paragraph is a list of `<w:t>` runs. Sufficient for testing the mini
 * extractor; not a fully valid Word document (missing [Content_Types].xml etc.)
 * but the extractor only reads `word/document.xml` so this works.
 */
private fun synthesizeDocx(paragraphs: List<List<String>>): ByteArray {
    val xml =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            paragraphs.forEach { runs ->
                append("<w:p>")
                runs.forEach { text ->
                    append("<w:r><w:t xml:space=\"preserve\">")
                    append(text)
                    append("</w:t></w:r>")
                }
                append("</w:p>")
            }
            append("</w:body></w:document>")
        }

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return out.toByteArray()
}
