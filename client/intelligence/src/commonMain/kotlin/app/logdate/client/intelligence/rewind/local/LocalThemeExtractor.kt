package app.logdate.client.intelligence.rewind.local

/**
 * Extracts a small list of theme keywords from a period's text entries by counting
 * content-word frequency and dropping noise (stopwords, short tokens, punctuation).
 *
 * The output feeds local Rewind narrative shaping in two ways:
 * 1. As the `themes` list on the synthetic `WeekNarrative` produced by the local
 *    Rewind strategy, so the sequencer can title beats / cards with them.
 * 2. As an upstream signal for activity derivation (e.g. a `travel`-heavy week
 *    drives the `TRAVEL` activity card).
 *
 * This is intentionally simple: no stemming, no n-grams, no TF-IDF. The local
 * strategy ships a "good enough" narrative shape — quality lives in the AI path.
 */
class LocalThemeExtractor(
    private val maxThemes: Int = DEFAULT_MAX_THEMES,
    private val minWordLength: Int = DEFAULT_MIN_WORD_LENGTH,
) {
    /**
     * @param entries raw entry contents for the period.
     * @return up to [maxThemes] lowercase theme keywords, ordered by descending
     *   frequency. Empty when the period has no content-bearing text.
     */
    fun extract(entries: List<String>): List<String> {
        if (entries.isEmpty()) return emptyList()
        val counts = mutableMapOf<String, Int>()
        for (entry in entries) {
            tokenize(entry).forEach { token ->
                if (token.length < minWordLength) return@forEach
                if (StopwordList.contains(token)) return@forEach
                counts[token] = (counts[token] ?: 0) + 1
            }
        }
        return counts
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(maxThemes)
            .map { it.key }
    }

    /**
     * Splits text on whitespace and punctuation, lowercases each token, and drops
     * empties. Kept inline so it has no platform dependency (no [java.util.regex]
     * unavailable in commonMain).
     */
    private fun tokenize(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text) {
            if (c.isLetter()) {
                current.append(c.lowercaseChar())
            } else if (current.isNotEmpty()) {
                out.add(current.toString())
                current.clear()
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private companion object {
        const val DEFAULT_MAX_THEMES: Int = 5
        const val DEFAULT_MIN_WORD_LENGTH: Int = 3
    }
}
