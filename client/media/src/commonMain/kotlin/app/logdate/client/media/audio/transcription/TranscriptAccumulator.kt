package app.logdate.client.media.audio.transcription

/**
 * Accumulates finalized transcript segments and a rolling partial result
 * into a single combined string.
 *
 * Finalized segments are punctuated text committed at endpoint boundaries.
 * The partial is the in-progress recognition text that hasn't hit an endpoint yet.
 */
class TranscriptAccumulator {
    private val segments = mutableListOf<String>()
    private val utterances = mutableListOf<TimedUtterance>()
    private var partial: String = ""

    fun addSegment(
        text: String,
        utterance: TimedUtterance? = null,
    ) {
        segments.add(text)
        if (utterance != null) {
            utterances += utterance
        }
        partial = ""
    }

    fun setPartial(text: String) {
        partial = text
    }

    fun build(): String {
        val base = segments.joinToString(" ")
        return if (partial.isNotBlank()) {
            if (base.isNotBlank()) "$base $partial" else partial
        } else {
            base
        }
    }

    fun buildTimedTranscript(): TimedTranscript? =
        utterances
            .takeIf { it.isNotEmpty() }
            ?.let(::TimedTranscript)

    fun reset() {
        segments.clear()
        utterances.clear()
        partial = ""
    }
}
