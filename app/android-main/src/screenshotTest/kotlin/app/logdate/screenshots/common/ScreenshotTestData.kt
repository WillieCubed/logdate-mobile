package app.logdate.screenshots.common

import app.logdate.shared.model.Journal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Shared fake data for screenshot tests.
 */
object ScreenshotTestData {
    /** Standard phone device spec for screenshots. */
    const val PHONE = "spec:width=411dp,height=891dp"

    /** Tablet device spec for adaptive layout screenshots. */
    const val TABLET = "spec:width=1280dp,height=800dp"

    /** Landscape phone device spec for adaptive layout screenshots. */
    const val PHONE_LANDSCAPE = "spec:width=891dp,height=411dp"

    private val baseTimestamp = Instant.fromEpochMilliseconds(1_740_000_000_000L) // ~Feb 2025

    val sampleJournal = Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        title = "Daily Reflections",
        description = "A space for everyday thoughts",
        isFavorited = true,
        created = baseTimestamp,
        lastUpdated = baseTimestamp,
    )

    val sampleJournal2 = Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
        title = "Travel Log",
        description = "Adventures and explorations",
        isFavorited = false,
        created = baseTimestamp,
        lastUpdated = baseTimestamp,
    )

    val sampleJournal3 = Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000003"),
        title = "Recipe Notes",
        description = "",
        isFavorited = false,
        created = baseTimestamp,
        lastUpdated = baseTimestamp,
    )

    val sampleJournals = listOf(sampleJournal, sampleJournal2, sampleJournal3)

    /** ~40 bars of fake waveform data. */
    val mockAmplitudes = listOf(
        0.3f, 0.5f, 0.4f, 0.7f, 0.6f, 0.8f, 0.9f, 0.7f, 0.5f, 0.6f,
        0.4f, 0.3f, 0.5f, 0.8f, 0.7f, 0.6f, 0.9f, 0.8f, 0.7f, 0.5f,
        0.4f, 0.6f, 0.7f, 0.5f, 0.4f, 0.6f, 0.8f, 0.9f, 0.7f, 0.6f,
        0.5f, 0.4f, 0.3f, 0.5f, 0.6f, 0.7f, 0.8f, 0.6f, 0.4f, 0.3f,
    )

    /** Audio levels for recording display. */
    val mockAudioLevels = listOf(
        0.2f, 0.5f, 0.3f, 0.8f, 0.6f, 0.9f, 0.4f, 0.7f, 0.5f, 0.3f,
        0.6f, 0.8f, 0.4f, 0.7f, 0.9f, 0.5f, 0.3f, 0.6f, 0.8f, 0.4f,
    )

    val baseInstant: Instant = baseTimestamp
}
