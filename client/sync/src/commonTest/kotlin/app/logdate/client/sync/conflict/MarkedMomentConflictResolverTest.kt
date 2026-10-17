package app.logdate.client.sync.conflict

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentConflictResolverTest {
    private val resolver = MarkedMomentConflictResolver()
    private val mark =
        MarkedMoment(
            Uuid.random(),
            Instant.fromEpochMilliseconds(1),
            "UTC",
            null,
            null,
            MarkState.OPEN,
            "original",
            emptySet(),
            "watch",
            Instant.fromEpochMilliseconds(2),
            0,
        )

    @Test fun `resolution state never regresses for any delivery ordering`() {
        for (a in MarkState.entries) {
            for (b in MarkState.entries) {
                assertEquals(maxOf(a, b), resolver.merge(mark.copy(state = a), mark.copy(state = b)).state)
            }
        }
    }

    @Test fun `concurrent resolutions retain both attached notes and highest cloud version`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val merged =
            resolver.merge(
                mark.copy(state = MarkState.DISMISSED, resolvedNoteIds = setOf(first), syncVersion = 12),
                mark.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(second), syncVersion = 3),
            )
        assertEquals(setOf(first, second), merged.resolvedNoteIds)
        assertEquals(MarkState.FILLED, merged.state)
        assertEquals(12L, merged.syncVersion)
    }

    @Test fun `newest scalar payload wins without reopening a filled mark`() {
        val newer =
            mark.copy(
                caption = "corrected",
                timeZoneId = "America/Los_Angeles",
                location = MarkLocation(36.1, -115.1, 10f),
                heartRateBpm = 72,
                lastUpdated = Instant.fromEpochMilliseconds(3),
            )
        assertEquals(newer.copy(state = MarkState.FILLED), resolver.merge(mark.copy(state = MarkState.FILLED), newer))
        assertEquals(newer.copy(state = MarkState.FILLED), resolver.merge(newer, mark.copy(state = MarkState.FILLED)))
    }

    @Test fun `equal timestamps converge without state or version biasing scalar election`() {
        val a = mark.copy(caption = "a", state = MarkState.FILLED, syncVersion = 20)
        val b = mark.copy(caption = "b", state = MarkState.DISMISSED, syncVersion = 1)
        assertEquals(b.copy(state = MarkState.FILLED, syncVersion = 20), resolver.merge(a, b))
        assertEquals(resolver.merge(a, b), resolver.merge(b, a))
    }

    @Test fun `merge is commutative associative and idempotent across independent edits`() {
        val variants =
            listOf(
                mark,
                mark.copy(caption = "new", lastUpdated = Instant.fromEpochMilliseconds(3)),
                mark.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(Uuid.random()), syncVersion = 4),
                mark.copy(state = MarkState.DISMISSED, location = MarkLocation(36.1, -115.1, 10f)),
                mark.copy(location = MarkLocation(36.2, -115.2, null), heartRateBpm = 80),
                mark.copy(timeZoneId = "Europe/London", resolvedNoteIds = setOf(Uuid.random()), syncVersion = 2),
            )
        for (a in variants) {
            assertEquals(a, resolver.merge(a, a))
            for (b in variants) {
                assertEquals(resolver.merge(a, b), resolver.merge(b, a))
                for (c in variants) assertEquals(resolver.merge(resolver.merge(a, b), c), resolver.merge(a, resolver.merge(b, c)))
            }
        }
    }

    @Test fun `the activity recorded at capture survives a merge with a copy that lacks it`() {
        val moving = mark.copy(capturedDuring = CaptureActivity.MOVING, lastUpdated = Instant.fromEpochMilliseconds(1))
        val newer = mark.copy(caption = "edited", lastUpdated = Instant.fromEpochMilliseconds(5))

        assertEquals(CaptureActivity.MOVING, resolver.merge(moving, newer).capturedDuring)
        assertEquals(CaptureActivity.MOVING, resolver.merge(newer, moving).capturedDuring)
    }

    @Test fun `different capture identities cannot be merged`() {
        for (other in listOf(
            mark.copy(id = Uuid.random()),
            mark.copy(capturedAt = Instant.fromEpochMilliseconds(9)),
            mark.copy(originDeviceId = "foreign"),
        )) {
            assertFailsWith<IllegalArgumentException> { resolver.merge(mark, other) }
        }
    }
}
