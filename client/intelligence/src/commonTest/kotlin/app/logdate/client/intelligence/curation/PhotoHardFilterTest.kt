package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PhotoHardFilterTest {
    private val filter = PhotoHardFilter()
    private val config = CurationConfig()

    private fun image(
        signals: MediaSignals = MediaSignals(),
        timestampMs: Long = 0L,
    ): MediaCandidate =
        MediaCandidate(
            media =
                IndexedMedia.Image(
                    uid = Uuid.random(),
                    uri = "test://photo",
                    timestamp = Instant.fromEpochMilliseconds(timestampMs),
                    caption = null,
                ),
            signals = signals,
        )

    @Test
    fun `keeps a clean photo with all-null signals`() {
        val outcome = filter.filter(listOf(image()), config)
        assertEquals(1, outcome.survivors.size)
        assertEquals(0, outcome.rejected.size)
    }

    @Test
    fun `rejects screenshot when flag is set`() {
        val outcome = filter.filter(listOf(image(MediaSignals(isLikelyScreenshot = true))), config)
        assertEquals(0, outcome.survivors.size)
        assertEquals(1, outcome.rejected.size)
        assertTrue(
            outcome.rejected
                .single()
                .reasons
                .contains(RejectReason.SCREENSHOT),
        )
    }

    @Test
    fun `keeps screenshot when config disables the rule`() {
        val outcome =
            filter.filter(
                listOf(image(MediaSignals(isLikelyScreenshot = true))),
                config.copy(excludeScreenshots = false),
            )
        assertEquals(1, outcome.survivors.size)
    }

    @Test
    fun `rejects doc scan when flag is set`() {
        val outcome = filter.filter(listOf(image(MediaSignals(isLikelyDocumentScan = true))), config)
        assertEquals(0, outcome.survivors.size)
        assertTrue(
            outcome.rejected
                .single()
                .reasons
                .contains(RejectReason.DOC_SCAN),
        )
    }

    @Test
    fun `rejects items below the minimum resolution`() {
        val outcome =
            filter.filter(
                listOf(image(MediaSignals(widthPx = 100, heightPx = 100))),
                config,
            )
        assertEquals(0, outcome.survivors.size)
        assertTrue(
            outcome.rejected
                .single()
                .reasons
                .contains(RejectReason.BELOW_MIN_RESOLUTION),
        )
    }

    @Test
    fun `keeps items with unknown dimensions`() {
        // Null width/height = "unknown", we accept rather than reject.
        val outcome =
            filter.filter(
                listOf(image(MediaSignals(widthPx = null, heightPx = null))),
                config,
            )
        assertEquals(1, outcome.survivors.size)
    }

    @Test
    fun `rejects unsupported MIME types`() {
        val outcome =
            filter.filter(
                listOf(image(MediaSignals(mimeType = "image/bmp"))),
                config,
            )
        assertEquals(0, outcome.survivors.size)
        assertTrue(
            outcome.rejected
                .single()
                .reasons
                .contains(RejectReason.UNSUPPORTED_MIME),
        )
    }

    @Test
    fun `accepts the supported MIME allowlist`() {
        val supported = listOf("image/jpeg", "image/png", "image/heic", "image/webp", "video/mp4", "video/quicktime")
        supported.forEach { mime ->
            val outcome = filter.filter(listOf(image(MediaSignals(mimeType = mime))), config)
            assertEquals(1, outcome.survivors.size, "expected $mime to be accepted")
        }
    }

    @Test
    fun `collapses an explicit burst group to one survivor`() {
        val members =
            (0 until 5).map { idx ->
                image(
                    signals =
                        MediaSignals(
                            burstGroupKey = "burst-A",
                            widthPx = 1000 + idx, // last has highest res
                            heightPx = 1000,
                        ),
                    timestampMs = idx * 1000L,
                )
            }
        val outcome = filter.filter(members, config)
        assertEquals(1, outcome.survivors.size)
        assertEquals(4, outcome.rejected.size)
        outcome.rejected.forEach { rejected ->
            assertTrue(rejected.reasons.contains(RejectReason.BURST_DUPLICATE))
        }
    }

    @Test
    fun `collapses time-clustered shots without a burst key`() {
        val members =
            (0 until 4).map { idx ->
                image(timestampMs = idx * 2_000L) // 2s apart — within default 30s burst window
            }
        val outcome = filter.filter(members, config)
        assertEquals(1, outcome.survivors.size)
        assertEquals(3, outcome.rejected.size)
        outcome.rejected.forEach { rejected ->
            assertTrue(rejected.reasons.contains(RejectReason.BURST_DUPLICATE))
        }
    }

    @Test
    fun `keeps shots well outside the burst window as separate items`() {
        val members =
            listOf(
                image(timestampMs = 0L),
                image(timestampMs = 60_000L), // 60s apart > 30s window
                image(timestampMs = 120_000L),
            )
        val outcome = filter.filter(members, config)
        assertEquals(3, outcome.survivors.size)
        assertEquals(0, outcome.rejected.size)
    }

    @Test
    fun `screenshot inside a burst still gets the screenshot verdict`() {
        // A screenshot that happens to share a burst with normal photos must keep the
        // SCREENSHOT verdict, not slip through as the burst representative.
        val members =
            listOf(
                image(MediaSignals(isLikelyScreenshot = true, burstGroupKey = "B1")),
                image(MediaSignals(burstGroupKey = "B1", widthPx = 1000, heightPx = 1000)),
            )
        val outcome = filter.filter(members, config)
        assertEquals(1, outcome.survivors.size)
        // Screenshot is in rejected with SCREENSHOT reason.
        assertTrue(
            outcome.rejected.any { it.reasons.contains(RejectReason.SCREENSHOT) },
            "expected screenshot reject reason among ${outcome.rejected.map { it.reasons }}",
        )
    }
}
