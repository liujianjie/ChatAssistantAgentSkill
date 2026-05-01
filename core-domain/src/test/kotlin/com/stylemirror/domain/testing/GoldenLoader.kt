package com.stylemirror.domain.testing

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/**
 * Loads YAML golden fixtures from `classpath:/golden/` into typed
 * [GoldenFixture] aggregates that downstream algorithm layers (T11 cleaning,
 * T12 speaker alignment, T14 profiling) can consume directly.
 *
 * Test-only — lives in core-domain's test source set, but algorithm modules
 * may depend on it via `testImplementation(testFixtures(...))` once that
 * source-set wiring is added (currently scheduled for T11).
 *
 * Parses with SnakeYAML's [SafeConstructor] (no arbitrary-class instantiation)
 * to keep the fixture format from accidentally turning into a deserialization
 * sink for future maintainers.
 */
object GoldenLoader {
    private const val CLASSPATH_DIR = "golden"
    private const val FIXTURE_GLOB = "*.yml"

    /** All fixtures, sorted by file name (== fixture id by convention). */
    fun loadAll(): List<GoldenFixture> = listFixturePaths().map(::loadFromPath)

    /** Single fixture by id; throws if missing. */
    fun load(id: String): GoldenFixture =
        loadAll().firstOrNull { it.id == id }
            ?: error("Golden fixture '$id' not found in classpath:/$CLASSPATH_DIR/")

    /** Raw input stream — for tests that want to assert on the YAML bytes themselves. */
    fun openStream(fileName: String): InputStream =
        requireNotNull(
            GoldenLoader::class.java.classLoader.getResourceAsStream("$CLASSPATH_DIR/$fileName"),
        ) { "Golden fixture file '$fileName' not found on classpath" }

    private fun listFixturePaths(): List<Path> {
        val url =
            requireNotNull(GoldenLoader::class.java.classLoader.getResource(CLASSPATH_DIR)) {
                "Golden fixture directory '$CLASSPATH_DIR' is not on the classpath"
            }
        val dir = Paths.get(url.toURI())
        return Files.newDirectoryStream(dir, FIXTURE_GLOB).use { stream ->
            stream.sortedBy { it.fileName.toString() }
        }
    }

    private fun loadFromPath(path: Path): GoldenFixture =
        Files.newBufferedReader(path).use { reader ->
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            val loaded = yaml.load<Any?>(reader) ?: error("Empty fixture: ${path.fileName}")

            @Suppress("UNCHECKED_CAST")
            val raw =
                (loaded as? Map<String, Any?>)
                    ?: error("Non-map fixture root: ${path.fileName}")
            parseFixture(raw, sourceName = path.fileName.toString())
        }

    @Suppress("UNCHECKED_CAST")
    private fun parseFixture(
        raw: Map<String, Any?>,
        sourceName: String,
    ): GoldenFixture {
        val id = raw.requireString("id", sourceName)
        val description = raw.requireString("description", sourceName)
        val myAliases = raw.requireStringList("my_aliases", sourceName)
        val partnerId = PartnerId(raw.requireString("partner_id", sourceName))
        val expectedCues = raw.requireStringList("expected_style_cues", sourceName)
        val rawMessages =
            (raw["messages"] as? List<Map<String, Any?>>)
                ?: error("$sourceName: 'messages' must be a non-empty list of maps")
        require(rawMessages.isNotEmpty()) { "$sourceName: 'messages' is empty" }

        val messages = rawMessages.mapIndexed { idx, m -> parseMessage(m, sourceName, idx) }
        val metadata = (raw["metadata"] as? Map<String, Any?>).orEmpty()

        return GoldenFixture(
            id = id,
            description = description,
            myAliases = myAliases,
            expectedStyleCues = expectedCues,
            metadata = metadata,
            rawMessages = rawMessages,
            context = ConversationContext(partnerId = partnerId, messages = messages),
        )
    }

    private fun parseMessage(
        raw: Map<String, Any?>,
        sourceName: String,
        index: Int,
    ): Message {
        val locator = "$sourceName[messages.$index]"
        val speaker = raw.requireString("speaker", locator)
        val id = MessageId(raw.requireString("id", locator))
        val content = raw.requireString("content", locator)
        val sentAt = parseInstant(raw["sent_at"], "$locator.sent_at")
        return when (speaker) {
            "me" -> Message.Mine(id = id, content = content, sentAt = sentAt)
            "theirs" ->
                Message.Theirs(
                    id = id,
                    content = content,
                    sentAt = sentAt,
                    displayName = raw.requireString("display_name", locator),
                )
            else -> error("$locator: unknown speaker '$speaker' (expected 'me' or 'theirs')")
        }
    }

    /**
     * SnakeYAML's [SafeConstructor] auto-parses ISO-8601 scalars into
     * [java.util.Date], which renders via the legacy `Day Mon DD ...` form when
     * round-tripped through [Any.toString]. Accept both shapes here so the
     * fixture YAML can keep timestamps unquoted for readability.
     */
    private fun parseInstant(
        raw: Any?,
        locator: String,
    ): Instant =
        when (raw) {
            null -> error("$locator: missing")
            is java.util.Date -> raw.toInstant()
            is Instant -> raw
            is String -> Instant.parse(raw)
            else -> Instant.parse(raw.toString())
        }
}

/**
 * Parsed result of one golden fixture file. Carries both the typed
 * [ConversationContext] (for algorithms that consume domain types) and the
 * raw YAML message maps (for algorithms that need fixture-only hints such as
 * per-message visible aliases that the domain model deliberately doesn't carry).
 */
data class GoldenFixture(
    val id: String,
    val description: String,
    val myAliases: List<String>,
    val expectedStyleCues: List<String>,
    val metadata: Map<String, Any?>,
    val rawMessages: List<Map<String, Any?>>,
    val context: ConversationContext,
) {
    val messages: List<Message> get() = context.messages

    /** Filename this fixture was loaded from — derived, by convention `<id>.yml`. */
    val sourceFile: String get() = "$id.yml"
}

private fun Map<String, Any?>.requireString(
    key: String,
    source: String,
): String {
    val v = this[key] ?: error("$source: missing '$key'")
    val s = v.toString()
    require(s.isNotBlank()) { "$source: '$key' is blank" }
    return s
}

private fun Map<String, Any?>.requireStringList(
    key: String,
    source: String,
): List<String> {
    val v = this[key] ?: error("$source: missing '$key'")
    require(v is List<*>) { "$source: '$key' must be a list" }
    return v.map { it?.toString() ?: error("$source: '$key' has null entry") }
}
