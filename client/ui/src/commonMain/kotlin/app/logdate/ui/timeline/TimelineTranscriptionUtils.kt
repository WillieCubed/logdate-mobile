package app.logdate.ui.timeline

import kotlin.uuid.Uuid

internal data class TranscriptExcerpt(
    val text: String,
    val hasMore: Boolean,
)

private val sentenceBoundaryRegex = Regex("(?<=[.!?])\\s+")

internal fun buildTranscriptExcerpt(transcript: String): TranscriptExcerpt? {
    val normalized = transcript.normalizeTranscriptWhitespace()
    if (normalized.isBlank()) return null

    val sentences = splitTranscriptSentences(normalized)
    if (sentences.isEmpty()) {
        return TranscriptExcerpt(
            text = normalized,
            hasMore = false,
        )
    }

    val excerptSentenceCount =
        if (sentences.size > 1 && sentences.first().length < SHORT_SENTENCE_LENGTH_THRESHOLD) {
            2
        } else {
            1
        }

    val excerpt = sentences.take(excerptSentenceCount).joinToString(" ")
    return TranscriptExcerpt(
        text = excerpt,
        hasMore = sentences.size > excerptSentenceCount || excerpt.length < normalized.length,
    )
}

internal fun collectLazyTimelineAudioNoteIds(
    items: List<TimelineDayUiState>,
    visibleDayIndices: Set<Int>,
    lookaheadCount: Int = DEFAULT_TRANSCRIPTION_LOOKAHEAD_DAYS,
): Set<Uuid> {
    if (items.isEmpty() || visibleDayIndices.isEmpty()) return emptySet()

    val firstVisibleIndex = visibleDayIndices.minOrNull() ?: return emptySet()
    val lastVisibleIndex = visibleDayIndices.maxOrNull() ?: return emptySet()
    val clampedStart = firstVisibleIndex.coerceAtLeast(0)
    val clampedEnd = (lastVisibleIndex + lookaheadCount).coerceAtMost(items.lastIndex)

    return buildSet {
        for (index in clampedStart..clampedEnd) {
            addAll(items[index].audioNoteIds())
        }
    }
}

internal fun TimelineDayUiState.audioNoteIds(): Set<Uuid> =
    buildSet {
        notes.filterIsInstance<AudioNoteUiState>().forEach { note ->
            add(note.noteId)
        }
        moments.forEach { moment ->
            moment.audio?.noteId?.let { noteId ->
                add(noteId)
            }
        }
    }

private fun splitTranscriptSentences(transcript: String): List<String> =
    transcript
        .split(sentenceBoundaryRegex)
        .map(String::trim)
        .filter(String::isNotEmpty)

private fun String.normalizeTranscriptWhitespace(): String =
    trim()
        .replace(Regex("\\s+"), " ")

internal const val DEFAULT_TRANSCRIPTION_LOOKAHEAD_DAYS = 2

private const val SHORT_SENTENCE_LENGTH_THRESHOLD = 20
