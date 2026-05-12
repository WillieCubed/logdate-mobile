package app.logdate.client.intelligence.rewind.local

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.HighlightedQuote

/**
 * Picks up to a few verbatim sentences from a period's text entries to render as
 * highlighted quotes in a local Rewind.
 *
 * Sentences score on three signals: first-person language (an "I" / "we" felt-it
 * sentence carries the user's voice), emotion words (positive or negative), and
 * length (40–180 chars — short enough to read in a card, long enough to mean
 * something). Leading / trailing sentences in an entry get a small penalty since
 * they tend to be setup / sign-off rather than the heart of the entry.
 *
 * Top scorers are returned verbatim; [HighlightedQuote.whyItHits] stays empty
 * because we can't honestly synthesize a reason without an LLM.
 */
class LocalQuoteSelector(
    private val maxQuotes: Int = DEFAULT_MAX_QUOTES,
    private val minSentenceLength: Int = DEFAULT_MIN_SENTENCE_LENGTH,
    private val maxSentenceLength: Int = DEFAULT_MAX_SENTENCE_LENGTH,
) {
    /**
     * @param entries text entries from the period.
     * @return up to [maxQuotes] verbatim sentences, ordered by descending score.
     */
    fun select(entries: List<JournalNote.Text>): List<HighlightedQuote> {
        if (entries.isEmpty()) return emptyList()

        data class Candidate(
            val text: String,
            val score: Int,
            val sourceEntryId: String,
        )

        val candidates = mutableListOf<Candidate>()
        for (entry in entries) {
            val sentences = splitSentences(entry.content)
            sentences.forEachIndexed { index, raw ->
                val sentence = raw.trim()
                if (sentence.length < minSentenceLength) return@forEachIndexed
                if (sentence.length > maxSentenceLength) return@forEachIndexed
                val score = scoreSentence(sentence, index, sentences.size)
                if (score <= 0) return@forEachIndexed
                candidates.add(Candidate(sentence, score, entry.uid.toString()))
            }
        }

        return candidates
            .sortedByDescending { it.score }
            .take(maxQuotes)
            .map { HighlightedQuote(text = it.text, whyItHits = "", sourceEntryId = it.sourceEntryId) }
    }

    /**
     * Splits text into sentences. Cuts on `.`, `!`, `?` followed by whitespace or
     * end-of-string. Kept inline so it has no platform regex dependency.
     */
    private fun splitSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            current.append(c)
            val terminal = c == '.' || c == '!' || c == '?'
            val followedByWs = (i + 1 >= text.length) || text[i + 1].isWhitespace()
            if (terminal && followedByWs) {
                out.add(current.toString())
                current.clear()
                while (i + 1 < text.length && text[i + 1].isWhitespace()) i++
            }
            i++
        }
        if (current.isNotBlank()) out.add(current.toString())
        return out
    }

    private fun scoreSentence(
        sentence: String,
        index: Int,
        totalSentences: Int,
    ): Int {
        var score = 0
        val lower = sentence.lowercase()
        if (containsFirstPerson(lower)) score += 2
        if (containsEmotionWord(lower)) score += 1
        // Leading / trailing sentences are usually framing — light penalty.
        if ((index == 0 || index == totalSentences - 1) && totalSentences > 2) score -= 1
        return score
    }

    private fun containsFirstPerson(lowercased: String): Boolean {
        // Match whole-word "i" / "me" / "we" / "us" / "my" / "our" — substring would
        // hit "in", "men", "wear", etc., which we don't want.
        for (pronoun in FIRST_PERSON_PRONOUNS) {
            if (wholeWordContains(lowercased, pronoun)) return true
        }
        return false
    }

    private fun containsEmotionWord(lowercased: String): Boolean {
        for (word in EMOTION_WORDS) {
            if (lowercased.contains(word)) return true
        }
        return false
    }

    private fun wholeWordContains(
        text: String,
        word: String,
    ): Boolean {
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val left = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val rightIdx = idx + word.length
            val right = rightIdx >= text.length || !text[rightIdx].isLetterOrDigit()
            if (left && right) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    private companion object {
        const val DEFAULT_MAX_QUOTES: Int = 3
        const val DEFAULT_MIN_SENTENCE_LENGTH: Int = 40
        const val DEFAULT_MAX_SENTENCE_LENGTH: Int = 180

        // First-person pronouns. Kept narrow — "i" only with whole-word boundaries.
        val FIRST_PERSON_PRONOUNS: Set<String> = setOf("i", "me", "we", "us", "my", "our")

        // Compact emotion-word seed list. Replaced by SentimentLexicon (Step 7d).
        val EMOTION_WORDS: Set<String> =
            setOf(
                // Positive
                "happy",
                "joy",
                "love",
                "excited",
                "grateful",
                "wonderful",
                "amazing",
                "beautiful",
                "thrilled",
                "proud",
                "delighted",
                "peaceful",
                "content",
                "blissful",
                "relieved",
                "hopeful",
                // Negative
                "sad",
                "angry",
                "frustrated",
                "anxious",
                "overwhelmed",
                "exhausted",
                "lonely",
                "miserable",
                "afraid",
                "disappointed",
                "heartbroken",
                "stressed",
                "worried",
                "regret",
                "hurt",
                "ashamed",
                // Engaged
                "alive",
                "energized",
                "focused",
                "curious",
                "moved",
                "inspired",
            )
    }
}
