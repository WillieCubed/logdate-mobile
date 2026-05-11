package app.logdate.client.intelligence.curation

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.Person
import app.logdate.shared.model.WeekNarrative
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Curates the media that should appear in a Rewind.
 *
 * Pipeline (each stage independently testable):
 *  1. [MediaSignalExtractor] produces platform signals.
 *  2. [PhotoHardFilter] drops screenshots / doc scans / tiny thumbs / unsupported MIME
 *     types / burst duplicates.
 *  3. [SignificanceScorer] composes a per-candidate [ScoreBreakdown].
 *  4. [BeatBucketer] assigns survivors to a story-beat time window.
 *  5. [DiversitySelector] picks the final per-beat photo set with diversity constraints
 *     and the global cap.
 *
 * The output [CurationResult] gives the sequencer (a) per-beat photo lists, (b) a
 * free-agent pool for structural panels, and (c) a `significanceScore` lookup so the
 * sequencer can stamp scores onto emitted `RewindContent.Image` / `Video` panels.
 *
 * Curation is best-effort: empty media list returns [CurationResult.EMPTY] rather than
 * failing, so a Rewind with only text content still generates.
 */
class RewindMediaCurator(
    private val signalExtractor: MediaSignalExtractor,
    private val hardFilter: PhotoHardFilter,
    private val scorer: SignificanceScorer,
    private val bucketer: BeatBucketer,
    private val selector: DiversitySelector,
) {
    suspend fun curate(
        allMedia: List<IndexedMedia>,
        narrative: WeekNarrative?,
        textEntries: List<JournalNote.Text>,
        people: List<Person>,
        locationHistory: List<LocationHistoryItem>,
        periodStart: Instant,
        periodEnd: Instant,
        config: CurationConfig,
    ): CurationResult {
        if (allMedia.isEmpty()) return CurationResult.EMPTY

        val signals = signalExtractor.extract(allMedia)
        val initial =
            allMedia.map { media ->
                MediaCandidate(media = media, signals = signals[media.uid] ?: MediaSignals())
            }

        val (afterHardFilter, hardRejects) = hardFilter.filter(initial, config).let { it.survivors to it.rejected }
        val scored = scorer.score(afterHardFilter, narrative, textEntries, people, locationHistory)
        val bucketed = bucketer.bucket(scored, narrative?.storyBeats.orEmpty(), textEntries, periodStart, periodEnd)
        val selection = selector.select(bucketed, config)

        val sigByMediaUid: Map<Uuid, Float> =
            scored.mapNotNull { c -> c.derivedScores?.let { c.media.uid to it.total } }.toMap()

        return CurationResult(
            perBeat = selection.perBeat,
            freeAgents = selection.freeAgents,
            rejected = hardRejects,
            sigByMediaUid = sigByMediaUid,
        )
    }
}
