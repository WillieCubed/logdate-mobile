package app.logdate.client.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Unit tests for the fallback chain that materializes [MediaObject]s from a
 * MediaStore cursor row. The contract under test:
 *
 * No matter how broken or incomplete a MediaStore row is, the user must never
 * see their media disappear from the gallery. Every field has a fallback path,
 * and the row is always materialized into a complete [MediaObject].
 *
 * These tests run on the JVM via `androidHostTest` — no emulator, no
 * Robolectric, no Android framework types. The seam is the [MediaCursorRow]
 * data class plus a fake [MediaRecoveryGateway].
 */
class MediaRowExtractionTest {
    private val fixedNow = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val nowProvider: () -> Instant = { fixedNow }

    private val sampleVideoUri = "content://media/external/video/media/42"
    private val sampleImageUri = "content://media/external/images/media/7"

    // ---------- Image fallbacks ----------

    @Test
    fun `image with all fields present materializes verbatim`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "vacation.jpg",
                sizeBytes = 4096,
                durationMillis = null,
                dateTakenMillis = 1_650_000_000_000L,
                dateAddedSeconds = null,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals(sampleImageUri, image.uri)
        assertEquals("vacation.jpg", image.name)
        assertEquals(4096, image.size)
        assertEquals(Instant.fromEpochMilliseconds(1_650_000_000_000L), image.timestamp)
    }

    @Test
    fun `image with null display name falls back to URI segment`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = null,
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals("7", image.name)
    }

    @Test
    fun `image with blank display name falls back to URI segment`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "   ",
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals("7", image.name)
    }

    @Test
    fun `image with null display name and empty URI segment falls back to Untitled`() {
        val row =
            MediaCursorRow(
                uri = "",
                displayName = null,
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals("Untitled", image.name)
    }

    @Test
    fun `image with null size falls back to gateway stat`() {
        val gateway = FakeRecoveryGateway(statSizeResult = 12_345L)
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = null,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val image = row.toImage(gateway, nowProvider)

        assertEquals(12_345, image.size)
        assertEquals(listOf(sampleImageUri), gateway.statRequests)
    }

    @Test
    fun `image with null size and unrecoverable gateway falls back to zero`() {
        val gateway = FakeRecoveryGateway(statSizeResult = null)
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = null,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val image = row.toImage(gateway, nowProvider)

        assertEquals(0, image.size)
    }

    @Test
    fun `image prefers DATE_TAKEN over DATE_ADDED`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = 1_650_000_000_000L,
                dateAddedSeconds = 1_660_000_000L,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals(Instant.fromEpochMilliseconds(1_650_000_000_000L), image.timestamp)
    }

    @Test
    fun `image with only DATE_ADDED converts seconds to milliseconds`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = null,
                dateAddedSeconds = 1_660_000_000L,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals(Instant.fromEpochMilliseconds(1_660_000_000_000L), image.timestamp)
    }

    @Test
    fun `image with no timestamp falls back to nowProvider`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = null,
                dateAddedSeconds = null,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals(fixedNow, image.timestamp)
    }

    @Test
    fun `image with zero timestamps treats them as missing`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = 0L,
                dateAddedSeconds = 0L,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        assertEquals(fixedNow, image.timestamp)
    }

    @Test
    fun `image with zero DATE_TAKEN falls back to DATE_ADDED rather than now`() {
        val row =
            MediaCursorRow(
                uri = sampleImageUri,
                displayName = "x.jpg",
                sizeBytes = 0,
                durationMillis = null,
                dateTakenMillis = 0L,
                dateAddedSeconds = 1_660_000_000L,
            )

        val image = row.toImage(NoopGateway, nowProvider)

        // DATE_TAKEN of 0 is meaningless (Android sentinel for unknown). The
        // fallback should pick DATE_ADDED rather than skip straight to now.
        assertEquals(Instant.fromEpochMilliseconds(1_660_000_000_000L), image.timestamp)
    }

    // ---------- Video fallbacks ----------

    @Test
    fun `video with all fields present materializes verbatim`() {
        val row =
            MediaCursorRow(
                uri = sampleVideoUri,
                displayName = "clip.mp4",
                sizeBytes = 1_000_000,
                durationMillis = 12_500L,
                dateTakenMillis = 1_650_000_000_000L,
                dateAddedSeconds = null,
            )

        val video = row.toVideo(NoopGateway, nowProvider)

        assertEquals(sampleVideoUri, video.uri)
        assertEquals("clip.mp4", video.name)
        assertEquals(1_000_000, video.size)
        assertEquals(12_500.milliseconds, video.duration)
        assertEquals(Instant.fromEpochMilliseconds(1_650_000_000_000L), video.timestamp)
    }

    @Test
    fun `video with null duration recovers from gateway`() {
        val gateway = FakeRecoveryGateway(readDurationResult = 30.seconds)
        val row =
            MediaCursorRow(
                uri = sampleVideoUri,
                displayName = "clip.mp4",
                sizeBytes = 1,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val video = row.toVideo(gateway, nowProvider)

        assertEquals(30.seconds, video.duration)
        assertEquals(listOf(sampleVideoUri), gateway.durationRequests)
    }

    @Test
    fun `video with null duration and unrecoverable gateway falls back to zero but stays visible`() {
        val gateway = FakeRecoveryGateway(readDurationResult = null)
        val row =
            MediaCursorRow(
                uri = sampleVideoUri,
                displayName = "clip.mp4",
                sizeBytes = 1,
                durationMillis = null,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val video = row.toVideo(gateway, nowProvider)

        // The critical contract: duration falls back to ZERO so the video still
        // appears in the gallery. The user must never see their video disappear.
        assertEquals(Duration.ZERO, video.duration)
        assertEquals(sampleVideoUri, video.uri)
        assertEquals("clip.mp4", video.name)
    }

    @Test
    fun `video with everything missing still materializes a complete record`() {
        val gateway = FakeRecoveryGateway(statSizeResult = null, readDurationResult = null)
        val row =
            MediaCursorRow(
                uri = sampleVideoUri,
                displayName = null,
                sizeBytes = null,
                durationMillis = null,
                dateTakenMillis = null,
                dateAddedSeconds = null,
            )

        val video = row.toVideo(gateway, nowProvider)

        // Every field has a safe fallback. The video is fully visible to the user.
        assertEquals(sampleVideoUri, video.uri)
        assertEquals("42", video.name)
        assertEquals(0, video.size)
        assertEquals(Duration.ZERO, video.duration)
        assertEquals(fixedNow, video.timestamp)
    }

    @Test
    fun `video gateway is not consulted when MediaStore has duration`() {
        val gateway = FakeRecoveryGateway(readDurationResult = 999.seconds)
        val row =
            MediaCursorRow(
                uri = sampleVideoUri,
                displayName = "clip.mp4",
                sizeBytes = 1,
                durationMillis = 5_000L,
                dateTakenMillis = 1L,
                dateAddedSeconds = null,
            )

        val video = row.toVideo(gateway, nowProvider)

        assertEquals(5_000.milliseconds, video.duration)
        assertEquals(emptyList<String>(), gateway.durationRequests)
    }

    // ---------- Test doubles ----------

    private object NoopGateway : MediaRecoveryGateway {
        override fun statFileSize(uri: String): Long? = null

        override fun readVideoDuration(uri: String): Duration? = null
    }

    private class FakeRecoveryGateway(
        private val statSizeResult: Long? = null,
        private val readDurationResult: Duration? = null,
    ) : MediaRecoveryGateway {
        val statRequests = mutableListOf<String>()
        val durationRequests = mutableListOf<String>()

        override fun statFileSize(uri: String): Long? {
            statRequests += uri
            return statSizeResult
        }

        override fun readVideoDuration(uri: String): Duration? {
            durationRequests += uri
            return readDurationResult
        }
    }
}
