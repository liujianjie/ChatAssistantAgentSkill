package com.stylemirror.app.onboarding

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Extracts plain text from import files in formats the user can drop in via
 * SAF: .txt / .md / .html / .docx / .pdf.
 *
 * **Why no Apache POI for .docx**: POI is a ~10 MB dependency and its Android
 * compatibility is fragile (XmlBeans / Oracle JDK assumptions). A `.docx` is
 * just a ZIP containing `word/document.xml`; the visible text lives in
 * `<w:t>` elements separated by `<w:p>` paragraph boundaries. A 30-line
 * extractor handles every chat-export shape we care about, with no body bloat.
 *
 * **PDF caveat**: PdfBox-Android extraction quality depends on how the PDF was
 * produced. Print-to-PDF from a chat app usually works; scanned-image PDFs do
 * not contain text and will return empty.
 *
 * Caller (MainActivity) is responsible for the 50 MB size guard before
 * passing bytes in — keeps this class free of Android `ContentResolver`
 * dependencies and trivially unit-testable.
 */
internal object TextExtractors {
    /**
     * Routes [bytes] to the right extractor by [mimeType]. Returns plain text.
     *
     * - `text/...` (.txt / .md / anything text-shaped) → UTF-8 decode
     * - `text/html` → Jsoup body text with newlines preserved
     * - `application/pdf` → PdfBox-Android text stripper
     * - `application/vnd.openxmlformats-officedocument.wordprocessingml.document` → mini docx extractor
     * - anything else → best-effort UTF-8 decode (chat exports often arrive
     *   with `application/octet-stream` from third-party tools)
     */
    fun extract(
        bytes: ByteArray,
        mimeType: String?,
    ): String {
        val mime = mimeType ?: ""
        return when {
            mime == "text/html" -> extractHtml(bytes)
            mime == "application/pdf" -> extractPdf(bytes)
            mime.endsWith("wordprocessingml.document") -> extractDocx(bytes)
            mime.startsWith("text/") -> bytes.toString(Charsets.UTF_8)
            else -> bytes.toString(Charsets.UTF_8)
        }
    }

    internal fun extractHtml(bytes: ByteArray): String {
        val doc = Jsoup.parse(bytes.toString(Charsets.UTF_8))
        val body = doc.body() ?: return doc.text()
        // wholeText() preserves only the source whitespace — for chat exports
        // where messages sit on separate <p>/<div>/<li>, we need to inject a
        // newline at each block-element boundary ourselves.
        val sb = StringBuilder()
        body.traverse(
            object : NodeVisitor {
                override fun head(
                    node: Node,
                    depth: Int,
                ) {
                    if (node is TextNode) sb.append(node.text())
                }

                override fun tail(
                    node: Node,
                    depth: Int,
                ) {
                    if (node is Element && node.tagName() in BLOCK_TAGS) sb.append('\n')
                }
            },
        )
        return sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    internal fun extractPdf(bytes: ByteArray): String =
        PDDocument.load(bytes).use { doc ->
            PDFTextStripper().getText(doc).trim()
        }

    /**
     * Mini .docx text extractor.
     *
     * `.docx` = ZIP archive. The text body lives in `word/document.xml`.
     * Paragraphs are bounded by `</w:p>`; runs of visible text by `<w:t>`.
     * We split on the former and regex-extract from the latter — adequate
     * for chat-export-shaped documents (no nested tables, footnotes, or
     * SmartArt).
     */
    internal fun extractDocx(bytes: ByteArray): String {
        val xml = readDocumentXml(bytes) ?: error("未找到 word/document.xml — 文件可能不是有效的 .docx")
        val paragraphs = xml.split("</w:p>")
        val textRegex = Regex("<w:t[^>]*>([^<]*)</w:t>")
        return paragraphs
            .map { para ->
                textRegex.findAll(para).joinToString("") { decodeXmlEntities(it.groupValues[1]) }
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun readDocumentXml(bytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return readZipEntry(zip)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun readZipEntry(zip: ZipInputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = zip.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    private fun decodeXmlEntities(s: String): String =
        s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private const val BUFFER_SIZE: Int = 8192

    private val BLOCK_TAGS: Set<String> =
        setOf(
            "p", "div", "br", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6",
            "blockquote", "section", "article",
        )
}
