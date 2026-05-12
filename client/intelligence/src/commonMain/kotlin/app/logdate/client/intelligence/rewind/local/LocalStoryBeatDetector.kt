package app.logdate.client.intelligence.rewind.local

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.StoryBeat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Splits a period's content into 1–6 [StoryBeat]s by clustering on calendar day. The
 * local Rewind path lacks an LLM to understand narrative arc; day-level clustering
 * is the simplest signal that still yields a story-shaped Rewind instead of one
 * catch-all beat covering everything.
 *
 * Each beat covers one day (or one "phase" when there are too many days to fit in
 * six beats), with:
 *  - a [StoryBeat.moment] headline picked from the longest entry-style sentence
 *    in the day's writing, or a day-of-week placeholder when nothing fits.
 *  - an [StoryBeat.emotionalWeight] tag inferred by counting feeling-laden words
 *    (joyful / heavy / mixed / quiet).
 *  - [StoryBeat.evidenceIds] containing the uid strings of every entry and media
 *    item from that day, so the sequencer can attach them to this beat.
 */
class LocalStoryBeatDetector(
    private val maxBeats: Int = DEFAULT_MAX_BEATS,
) {
    /**
     * @param textEntries text entries from the period.
     * @param media media items from the period (used only for evidence-id grouping).
     * @param periodStart inclusive start of the period.
     * @param periodEnd exclusive end of the period.
     * @return 0–[maxBeats] story beats. Empty when neither text nor media exists.
     */
    fun detect(
        textEntries: List<JournalNote.Text>,
        periodStart: Instant,
        periodEnd: Instant,
        media: List<IndexedMedia> = emptyList(),
    ): List<StoryBeat> {
        if (textEntries.isEmpty() && media.isEmpty()) return emptyList()
        val tz = TimeZone.currentSystemDefault()

        // Bucket entries + media by calendar date.
        val textByDay = textEntries.groupBy { it.creationTimestamp.toLocalDateTime(tz).date }
        val mediaByDay = media.groupBy { it.timestamp.toLocalDateTime(tz).date }

        val daysWithContent =
            (textByDay.keys + mediaByDay.keys)
                .toSortedSet()
                .toList()

        // If we have more days than beats allowed, collapse the oldest days into a
        // single opening "phase" beat.
        val dayBuckets =
            if (daysWithContent.size <= maxBeats) {
                daysWithContent.map { listOf(it) }
            } else {
                val collapseCount = daysWithContent.size - maxBeats + 1
                listOf(daysWithContent.take(collapseCount)) +
                    daysWithContent.drop(collapseCount).map { listOf(it) }
            }

        return dayBuckets.map { dayBucket ->
            val bucketEntries = dayBucket.flatMap { textByDay[it].orEmpty() }
            val bucketMedia = dayBucket.flatMap { mediaByDay[it].orEmpty() }
            buildBeat(dayBucket, bucketEntries, bucketMedia)
        }
    }

    private fun buildBeat(
        dayBucket: List<kotlinx.datetime.LocalDate>,
        entries: List<JournalNote.Text>,
        media: List<IndexedMedia>,
    ): StoryBeat {
        val evidenceIds =
            entries.map { it.uid.toString() } +
                media.map { it.uid.toString() }
        val moment = pickMoment(entries, dayBucket)
        val emotionalWeight = classifyEmotion(entries)
        return StoryBeat(
            moment = moment,
            context = "",
            emotionalWeight = emotionalWeight,
            evidenceIds = evidenceIds,
        )
    }

    private fun pickMoment(
        entries: List<JournalNote.Text>,
        dayBucket: List<kotlinx.datetime.LocalDate>,
    ): String {
        // Concatenate entry text, split sentences, take the first that fits the
        // headline window.
        val combined = entries.joinToString(separator = " ") { it.content.trim() }
        val sentence =
            splitSentences(combined)
                .map { it.trim() }
                .firstOrNull { it.length in HEADLINE_MIN..HEADLINE_MAX }
        if (sentence != null) return sentence

        // Fallback: day label or phase label.
        return if (dayBucket.size == 1) {
            dayBucket.first().toString()
        } else {
            "${dayBucket.first()} → ${dayBucket.last()}"
        }
    }

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

    private fun classifyEmotion(entries: List<JournalNote.Text>): String {
        if (entries.isEmpty()) return "quiet"
        val joined = entries.joinToString(separator = " ") { it.content }.lowercase()
        var positive = 0
        var negative = 0
        for (word in POSITIVE_WORDS) {
            if (joined.contains(word)) positive++
        }
        for (word in NEGATIVE_WORDS) {
            if (joined.contains(word)) negative++
        }
        return when {
            positive == 0 && negative == 0 -> "quiet"
            positive >= 2 * (negative + 1) -> "joyful"
            negative >= 2 * (positive + 1) -> "heavy"
            else -> "mixed"
        }
    }

    private companion object {
        const val DEFAULT_MAX_BEATS: Int = 6
        const val HEADLINE_MIN: Int = 20
        const val HEADLINE_MAX: Int = 120

        val POSITIVE_WORDS: Set<String> =
            setOf(
                "happy",
                "joy",
                "joyful",
                "love",
                "excited",
                "grateful",
                "wonderful",
                "amazing",
                "thrilled",
                "proud",
                "delighted",
                "peaceful",
                "blissful",
                "relieved",
                "hopeful",
                "great",
            )

        val NEGATIVE_WORDS: Set<String> =
            setOf(
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
                "wrong",
            )
    }
}
