package app.logdate.client.intelligence.rewind.local

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.StoryBeat
import kotlinx.datetime.LocalDate
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
     * @param locationHistory recorded GPS points from the period. When a single day
     *   shows visits to >=2 places more than [LOCATION_SPLIT_DISTANCE_METERS] apart,
     *   the detector subdivides that day into multiple beats — so trip days don't
     *   flatten into one undifferentiated entry.
     * @param periodStart inclusive start of the period.
     * @param periodEnd exclusive end of the period.
     * @return 0–[maxBeats] story beats. Empty when neither text nor media exists.
     */
    fun detect(
        textEntries: List<JournalNote.Text>,
        periodStart: Instant,
        periodEnd: Instant,
        media: List<IndexedMedia> = emptyList(),
        locationHistory: List<LocationHistoryItem> = emptyList(),
    ): List<StoryBeat> {
        if (textEntries.isEmpty() && media.isEmpty()) return emptyList()
        val tz = TimeZone.currentSystemDefault()

        // Bucket entries + media by calendar date.
        val textByDay = textEntries.groupBy { it.creationTimestamp.toLocalDateTime(tz).date }
        val mediaByDay = media.groupBy { it.timestamp.toLocalDateTime(tz).date }
        val locationsByDay =
            locationHistory.groupBy { it.timestamp.toLocalDateTime(tz).date }

        // Set + Set deduplicates (Set semantics), .sorted() yields a List in
        // natural date order; both available cross-platform unlike toSortedSet.
        val daysWithContent =
            (textByDay.keys + mediaByDay.keys).sorted()

        // Step 1: per-day, subdivide into segments by location cluster when the
        // user travelled meaningfully. Each segment becomes a candidate beat.
        val candidateSegments =
            daysWithContent.flatMap { day ->
                buildDaySegments(
                    day = day,
                    dayEntries = textByDay[day].orEmpty(),
                    dayMedia = mediaByDay[day].orEmpty(),
                    dayLocations = locationsByDay[day].orEmpty(),
                )
            }

        // Step 2: enforce the ≤[maxBeats] cap. Prefer keeping the most recent
        // segments intact (the "what happened at the end of the week" story);
        // collapse the earliest into a single opening "phase" beat when needed.
        val beatedSegments =
            if (candidateSegments.size <= maxBeats) {
                candidateSegments.map { listOf(it) }
            } else {
                val collapseCount = candidateSegments.size - maxBeats + 1
                listOf(candidateSegments.take(collapseCount)) +
                    candidateSegments.drop(collapseCount).map { listOf(it) }
            }

        return beatedSegments.map { group ->
            val days = group.flatMap { it.days }.distinct()
            val entries = group.flatMap { it.entries }
            val media = group.flatMap { it.media }
            buildBeat(days, entries, media)
        }
    }

    /**
     * Breaks one day into 1+ segments by location cluster. Returns segments in
     * chronological order; a day with no location data or a single cluster
     * returns a single segment with all that day's content.
     */
    private fun buildDaySegments(
        day: LocalDate,
        dayEntries: List<JournalNote.Text>,
        dayMedia: List<IndexedMedia>,
        dayLocations: List<LocationHistoryItem>,
    ): List<DaySegment> {
        if (dayLocations.size < 2) {
            return listOf(DaySegment(listOf(day), dayEntries, dayMedia))
        }

        val clusters = clusterByDistance(dayLocations.sortedBy { it.timestamp })
        if (clusters.size < 2) {
            return listOf(DaySegment(listOf(day), dayEntries, dayMedia))
        }

        // Assign each entry / media to the cluster whose timestamp range it falls within.
        val perCluster =
            clusters.map { cluster ->
                val clusterStart = cluster.first().timestamp
                val clusterEnd = cluster.last().timestamp
                val matchingEntries =
                    dayEntries.filter {
                        it.creationTimestamp >= clusterStart && it.creationTimestamp <= clusterEnd
                    }
                val matchingMedia =
                    dayMedia.filter {
                        it.timestamp >= clusterStart && it.timestamp <= clusterEnd
                    }
                DaySegment(
                    days = listOf(day),
                    entries = matchingEntries,
                    media = matchingMedia,
                )
            }
        val nonEmpty = perCluster.filter { it.entries.isNotEmpty() || it.media.isNotEmpty() }
        // If filtering wiped everything, keep the whole-day segment.
        return nonEmpty.ifEmpty { listOf(DaySegment(listOf(day), dayEntries, dayMedia)) }
    }

    /**
     * Greedy temporal clustering — walk locations in order and start a new
     * cluster whenever the next point is more than [LOCATION_SPLIT_DISTANCE_METERS]
     * from the current cluster's last point. Captures "morning at home, afternoon
     * at the coast" without trying to be a real geo-clusterer.
     */
    private fun clusterByDistance(sorted: List<LocationHistoryItem>): List<List<LocationHistoryItem>> {
        if (sorted.isEmpty()) return emptyList()
        val clusters = mutableListOf<MutableList<LocationHistoryItem>>()
        var current = mutableListOf(sorted.first())
        clusters.add(current)
        for (point in sorted.drop(1)) {
            val last = current.last()
            val distance = point.location.distanceTo(last.location)
            if (distance >= LOCATION_SPLIT_DISTANCE_METERS) {
                current = mutableListOf(point)
                clusters.add(current)
            } else {
                current.add(point)
            }
        }
        return clusters
    }

    private data class DaySegment(
        val days: List<LocalDate>,
        val entries: List<JournalNote.Text>,
        val media: List<IndexedMedia>,
    )

    private fun buildBeat(
        dayBucket: List<LocalDate>,
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
        dayBucket: List<LocalDate>,
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
        val total = positive + negative
        if (total == 0) return "quiet"
        // Ratio-based: a strong lean (≥70% on one side) classifies as that side,
        // otherwise "mixed". The previous binary scheme tipped to "mixed" the
        // moment a single contrasting word appeared in an otherwise positive day.
        val positiveRatio = positive.toDouble() / total
        return when {
            positiveRatio >= MAJORITY_THRESHOLD -> "joyful"
            (1.0 - positiveRatio) >= MAJORITY_THRESHOLD -> "heavy"
            else -> "mixed"
        }
    }

    private companion object {
        const val DEFAULT_MAX_BEATS: Int = 6
        const val HEADLINE_MIN: Int = 20
        const val HEADLINE_MAX: Int = 120
        private const val MAJORITY_THRESHOLD: Double = 0.70

        // Split a day into multiple beats when GPS points stretch this far apart.
        // 2km is "drove or took transit" — not "walked across the block."
        const val LOCATION_SPLIT_DISTANCE_METERS: Double = 2000.0

        // Journal-vocabulary expanded; "energized", "calm", "alive", and common
        // multi-word phrases like "feel good" are real journal language.
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
                "energized",
                "calm",
                "alive",
                "fulfilled",
                "content",
                "inspired",
                "playful",
                "tender",
                "warm",
                "feel good",
                "loved",
                "lucky",
                "blessed",
                "centered",
                "gentle",
                "sweet",
                "refreshed",
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
                "burnt out",
                "drained",
                "restless",
                "tense",
                "numb",
                "empty",
                "tired",
                "fed up",
                "grief",
                "lost",
                "stuck",
                "scared",
                "rejected",
                "ignored",
                "irritable",
                "panicked",
                "ashamed",
                "hopeless",
                "discouraged",
            )
    }
}
