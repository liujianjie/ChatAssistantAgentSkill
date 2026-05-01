package com.stylemirror.feature.realtime.input

import com.stylemirror.domain.conversation.ConversationContext
import com.stylemirror.domain.conversation.Message
import com.stylemirror.domain.conversation.MessageId
import com.stylemirror.domain.conversation.PartnerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * One "the user pasted a chat transcript into the app" event. Held as a value
 * so [PasteInput] stays purely functional — the caller (UI layer or test) is
 * responsible for plumbing the actual paste source into the upstream [Flow].
 *
 * @property rawText  Verbatim text the user pasted; line-broken on `\n`/`\r\n`.
 * @property partnerId Stable handle for the other side of the thread. Provided
 *   by the UI (e.g. from a contact picker) — adapter never invents one.
 * @property myAliases Display names the user goes by in this conversation.
 *   Used to overrule the default "lines starting with `我：` are mine"
 *   heuristic when the user uses a Latin alias instead.
 * @property overrides Manual per-line speaker corrections, keyed by 0-based
 *   *non-empty* line index. Wins over every heuristic; T08 surfaces this as a
 *   tap-to-flip UI affordance.
 */
data class PasteEvent(
    val rawText: String,
    val partnerId: PartnerId,
    val myAliases: Set<String> = emptySet(),
    val overrides: Map<Int, Speaker> = emptyMap(),
) {
    /** Local-only speaker enum — DO NOT leak into core-domain. */
    enum class Speaker { ME, THEIRS }
}

/** Convenience re-export so call sites don't need the nested-class path. */
typealias Speaker = PasteEvent.Speaker

/**
 * Paste-driven [InputAdapter]. Parses each upstream [PasteEvent] into a
 * [ConversationContext] without any blocking I/O.
 *
 * Heuristic order (highest priority first):
 *  1. `PasteEvent.overrides[lineIndex]` — explicit user correction.
 *  2. `prefix == "我"` (Chinese) — Mine.
 *  3. `prefix in myAliases` (case-sensitive; matches T11 prep notes) — Mine,
 *     `displayName` set to the matched prefix.
 *  4. Any other `prefix:` / `prefix：` form — Theirs with that displayName.
 *  5. Bare line (no prefix) — inherits the previous emitted message's speaker.
 *     If the bare line is the very first non-empty line we default to
 *     [Speaker.ME] because the most common paste shape is "user shares
 *     something they wrote first".
 *
 * Continuation lines (case 5) currently produce a *separate* message with the
 * inherited speaker — merging adjacent same-speaker fragments is left to T11
 * (data cleaning) so this layer stays a faithful 1:1 of what the user pasted.
 *
 * Timestamps are synthetic: `baseTime + lineIndex * 1s`. This is a deliberate
 * placeholder for the paste path — the real timestamp story arrives with T17
 * OCR (which can read the chat client's own time labels). Time arithmetic is
 * driven by an injectable [Clock] so tests can assert exact instants without
 * relying on wall-clock time.
 */
class PasteInput(
    private val pastes: Flow<PasteEvent>,
    private val clock: Clock = Clock.systemUTC(),
    private val flowContext: CoroutineContext = EmptyCoroutineContext,
) : InputAdapter {
    override fun receive(): Flow<ConversationContext> {
        val indexed =
            flow {
                var idx = 0
                pastes.collect { event ->
                    emit(idx to event)
                    idx++
                }
            }
        return indexed
            .map { (eventIndex, event) -> parse(event, eventIndex) }
            .flowOn(flowContext)
    }

    private fun parse(
        event: PasteEvent,
        eventIndex: Int,
    ): ConversationContext {
        val lines = splitNonEmptyLines(event.rawText)
        if (lines.isEmpty()) {
            // T07-decision: empty paste yields empty context, not an error.
            // DomainError.ImportFailureReason.EMPTY_INPUT exists, but signalling
            // it from a Flow<ConversationContext> would require widening the
            // adapter return type to Outcome<>; we keep the boundary narrow and
            // let downstream candidate generation refuse on `messages.isEmpty()`.
            return ConversationContext(partnerId = event.partnerId, messages = emptyList())
        }

        val baseInstant = clock.instant()
        var lastSpeaker: Speaker? = null
        val messages = ArrayList<Message>(lines.size)

        lines.forEachIndexed { lineIndex, line ->
            val parsed =
                classify(
                    line = line,
                    myAliases = event.myAliases,
                    override = event.overrides[lineIndex],
                    lastSpeaker = lastSpeaker,
                )
            lastSpeaker = parsed.speaker
            val sentAt = baseInstant.plus(Duration.ofSeconds(lineIndex.toLong()))
            val id = MessageId("paste-$eventIndex-$lineIndex")
            messages += buildMessage(parsed, id, sentAt)
        }
        return ConversationContext(partnerId = event.partnerId, messages = messages)
    }

    private fun classify(
        line: String,
        myAliases: Set<String>,
        override: Speaker?,
        lastSpeaker: Speaker?,
    ): ClassifiedLine {
        // Override wins outright. We still try to extract a label so display
        // names render sensibly even when the user flipped the speaker.
        val match = SPEAKER_REGEX.matchEntire(line)
        val rawLabel = match?.groups?.get("who")?.value?.trim()
        val rawContent = match?.groups?.get("content")?.value?.trim()

        if (override != null) {
            val content = rawContent ?: line.trim()
            val name = rawLabel ?: chooseFallbackName(override, myAliases)
            return ClassifiedLine(speaker = override, displayName = name, content = content)
        }

        if (match != null && rawLabel != null && rawContent != null) {
            return when {
                rawLabel == ME_TAG_ZH -> ClassifiedLine(Speaker.ME, rawLabel, rawContent)
                rawLabel in myAliases -> ClassifiedLine(Speaker.ME, rawLabel, rawContent)
                else -> ClassifiedLine(Speaker.THEIRS, rawLabel, rawContent)
            }
        }

        // Bare line: inherit previous speaker, or default to ME on first line.
        val inherited = lastSpeaker ?: Speaker.ME
        val fallbackName = chooseFallbackName(inherited, myAliases)
        return ClassifiedLine(speaker = inherited, displayName = fallbackName, content = line.trim())
    }

    private fun chooseFallbackName(
        speaker: Speaker,
        myAliases: Set<String>,
    ): String =
        when (speaker) {
            Speaker.ME -> myAliases.firstOrNull() ?: ME_TAG_ZH
            // Paste flow has no reliable handle for "the other side" when the
            // line itself omits a label — placeholder is fine; speaker
            // alignment (T12) revisits this once we have real captures.
            Speaker.THEIRS -> THEIRS_FALLBACK_NAME
        }

    private fun buildMessage(
        parsed: ClassifiedLine,
        id: MessageId,
        sentAt: Instant,
    ): Message =
        when (parsed.speaker) {
            Speaker.ME ->
                Message.Mine(
                    id = id,
                    content = parsed.content,
                    sentAt = sentAt,
                )
            Speaker.THEIRS ->
                Message.Theirs(
                    id = id,
                    content = parsed.content,
                    sentAt = sentAt,
                    displayName = parsed.displayName.ifBlank { THEIRS_FALLBACK_NAME },
                )
        }

    private data class ClassifiedLine(
        val speaker: Speaker,
        val displayName: String,
        val content: String,
    )

    companion object {
        private const val ME_TAG_ZH = "我"
        private const val THEIRS_FALLBACK_NAME = "对方"

        // `who` is bounded to ≤ 20 chars to keep the heuristic from eating an
        // entire long line that just happens to contain a colon partway through.
        // Both ASCII `:` and full-width `：` are accepted because Chinese chat
        // exports routinely mix them.
        private val SPEAKER_REGEX =
            Regex("""^\s*(?<who>[^:：\s][^:：]{0,19})[:：]\s*(?<content>.+)$""")

        private fun splitNonEmptyLines(raw: String): List<String> =
            raw.split('\n')
                .map { it.trimEnd('\r').trim() }
                .filter { it.isNotEmpty() }
    }
}
