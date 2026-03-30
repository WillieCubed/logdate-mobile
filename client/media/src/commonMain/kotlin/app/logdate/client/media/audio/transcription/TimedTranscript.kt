package app.logdate.client.media.audio.transcription

/**
 * Time-aligned transcript data for an audio recording.
 */
data class TimedTranscript(
    val utterances: List<TimedUtterance>,
) {
    val plainText: String
        get() = utterances.joinToString(" ") { it.text }.trim()
}

/**
 * A finalized utterance with absolute timing within a recording.
 */
data class TimedUtterance(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<TimedWord> = emptyList(),
) {
    init {
        require(endMs >= startMs) { "Utterance endMs must be >= startMs" }
    }
}

/**
 * A single searchable word with absolute timing within a recording.
 */
data class TimedWord(
    val text: String,
    val normalizedText: String,
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(endMs >= startMs) { "Word endMs must be >= startMs" }
    }
}

object TimedTranscriptBuilder {
    private const val TOKEN_WORD_PREFIX = "\u2581"
    private const val FALLBACK_LAST_WORD_DURATION_MS = 200L
    private val punctuationTokens = setOf(".", ",", "!", "?", ";", ":")
    private val ignoredTokens = setOf("<blk>", "<s>", "</s>")

    fun buildUtterance(
        text: String,
        utteranceStartMs: Long,
        utteranceConsumedMs: Long,
        tokens: List<String>,
        timestampsSeconds: List<Float>,
    ): TimedUtterance? {
        if (text.isBlank()) return null

        val wordStarts = mutableListOf<WordStart>()
        var currentWord: StringBuilder? = null
        var currentStartMs: Long? = null

        tokens.zip(timestampsSeconds).forEach { (rawToken, timestampSeconds) ->
            val token = rawToken.trim()
            if (token.isEmpty() || token in ignoredTokens) {
                return@forEach
            }

            if (token in punctuationTokens) {
                return@forEach
            }

            val timestampMs = utteranceStartMs + (timestampSeconds * 1000f).toLong()

            if (token.startsWith(TOKEN_WORD_PREFIX)) {
                flushWord(currentWord, currentStartMs, wordStarts)
                currentWord = StringBuilder(token.removePrefix(TOKEN_WORD_PREFIX))
                currentStartMs = timestampMs
            } else {
                if (currentWord == null) {
                    currentWord = StringBuilder(token)
                    currentStartMs = timestampMs
                } else {
                    currentWord.append(token)
                }
            }
        }

        flushWord(currentWord, currentStartMs, wordStarts)

        if (wordStarts.isEmpty()) {
            val endMs = utteranceStartMs + utteranceConsumedMs.coerceAtLeast(1L)
            return TimedUtterance(
                text = text,
                startMs = utteranceStartMs,
                endMs = endMs,
            )
        }

        val utteranceEndMsCandidate = utteranceStartMs + utteranceConsumedMs
        val lastWordStart = wordStarts.last().startMs
        val fallbackLastWordEnd =
            minOf(
                utteranceEndMsCandidate,
                lastWordStart + FALLBACK_LAST_WORD_DURATION_MS,
            ).coerceAtLeast(lastWordStart)

        val words =
            wordStarts.mapIndexed { index, word ->
                val nextStartMs = wordStarts.getOrNull(index + 1)?.startMs
                val endMs =
                    when {
                        nextStartMs != null -> maxOf(word.startMs, nextStartMs)
                        else -> fallbackLastWordEnd
                    }

                TimedWord(
                    text = word.text,
                    normalizedText = normalize(word.text),
                    startMs = word.startMs,
                    endMs = endMs,
                )
            }

        return TimedUtterance(
            text = text,
            startMs = words.first().startMs,
            endMs = words.last().endMs,
            words = words,
        )
    }

    private fun flushWord(
        currentWord: StringBuilder?,
        currentStartMs: Long?,
        words: MutableList<WordStart>,
    ) {
        val startMs = currentStartMs ?: return
        val text = currentWord?.toString().orEmpty().trim()
        if (text.isBlank()) return
        words += WordStart(text = text, startMs = startMs)
    }

    private fun normalize(text: String): String =
        text
            .trim()
            .lowercase()
            .filter { it.isLetterOrDigit() || it == '\'' }

    private data class WordStart(
        val text: String,
        val startMs: Long,
    )
}
