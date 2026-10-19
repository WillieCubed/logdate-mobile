package app.logdate.client.sync.space

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.metadata.EntityType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Marked moments syncing between two devices through a personal space. */
class SpaceMarkedMomentsTest {
    private val captured = Instant.parse("2026-10-19T14:03:27.481Z")
    private val home = FakeDataHome()
    private val watch = HistorySpaceDevice(home)
    private val phone = HistorySpaceDevice(home)

    private fun mark(id: Uuid = Uuid.random()) =
        MarkedMoment(
            id = id,
            capturedAt = captured,
            timeZoneId = "America/Chicago",
            location = MarkLocation(41.8781136, -87.6297982, 12.5f),
            heartRateBpm = 92,
            state = MarkState.OPEN,
            caption = "Saw the heron",
            resolvedNoteIds = emptySet(),
            originDeviceId = "watch-7f3a",
            lastUpdated = captured,
            syncVersion = 0,
            capturedDuring = CaptureActivity.MOVING,
        )

    @Test
    fun `a mark made on one device reaches another with everything it holds`() =
        runTest {
            val made = mark()
            watch.marks.mark(made)

            val uploaded = watch.manager.uploadPendingChanges()
            val downloaded = phone.manager.downloadRemoteChanges()

            assertTrue(uploaded.success, uploaded.errors.toString())
            assertTrue(downloaded.success, downloaded.errors.toString())
            assertEquals(made.copy(syncVersion = 1), phone.marks.getMark(made.id))
            assertNotNull(home.open("app.logdate.journal.markedMoment", made.id.toString()))
            assertEquals(emptyList(), watch.pending(EntityType.MOMENT))
        }

    @Test
    fun `the mark is stored as a record never sent to the marked moment endpoints`() =
        runTest {
            val made = mark()
            watch.marks.mark(made)

            val uploaded = watch.manager.uploadPendingChanges()

            assertTrue(uploaded.success, uploaded.errors.toString())
            val record = home.open("app.logdate.journal.markedMoment", made.id.toString())!!.record.jsonObject
            assertEquals("open", record["state"]?.jsonPrimitive?.content)
            assertEquals("watch-7f3a", record["originDevice"]?.jsonPrimitive?.content)
        }

    @Test
    fun `filling a mark on one device and dismissing it on another settles on filled everywhere`() =
        runTest {
            val made = mark()
            watch.marks.mark(made)
            watch.manager.uploadPendingChanges()
            phone.manager.downloadRemoteChanges()
            val noteId = Uuid.random()
            watch.marks.fill(made.id, "Wrote it up", setOf(noteId), Instant.parse("2026-10-19T18:00:00Z"))
            phone.marks.rows[made.id] =
                phone.marks.rows
                    .getValue(made.id)
                    .copy(state = MarkState.DISMISSED, lastUpdated = Instant.parse("2026-10-19T17:00:00Z"))
            phone.metadata.enqueuePending(made.id.toString(), EntityType.MOMENT, app.logdate.client.sync.metadata.PendingOperation.UPDATE)

            assertTrue(watch.manager.uploadPendingChanges().success)
            val phoneUpload = phone.manager.uploadPendingChanges()
            assertTrue(phoneUpload.success, phoneUpload.errors.toString())
            assertTrue(watch.manager.downloadRemoteChanges().success)

            val onPhone = phone.marks.getMark(made.id)!!
            val onWatch = watch.marks.getMark(made.id)!!
            assertEquals(MarkState.FILLED, onPhone.state)
            assertEquals(setOf(noteId), onPhone.resolvedNoteIds)
            assertEquals(onPhone.copy(syncVersion = 0), onWatch.copy(syncVersion = 0))
        }

    @Test
    fun `a mark deleted on one device is deleted on the other`() =
        runTest {
            val made = mark()
            watch.marks.mark(made)
            watch.manager.uploadPendingChanges()
            phone.manager.downloadRemoteChanges()
            assertNotNull(phone.marks.getMark(made.id))

            watch.marks.delete(made.id, Instant.parse("2026-10-19T19:00:00Z"))
            val uploaded = watch.manager.uploadPendingChanges()
            val downloaded = phone.manager.downloadRemoteChanges()

            assertTrue(uploaded.success, uploaded.errors.toString())
            assertTrue(downloaded.success, downloaded.errors.toString())
            assertNull(phone.marks.getMark(made.id))
            assertTrue(phone.marks.deleted.containsKey(made.id))
            assertNull(home.open("app.logdate.journal.markedMoment", made.id.toString()))
        }

    @Test
    fun `a mark that didn't change is not written again`() =
        runTest {
            watch.marks.mark(mark())
            watch.manager.uploadPendingChanges()
            val writes = home.writes

            watch.manager.uploadPendingChanges()
            phone.manager.downloadRemoteChanges()
            phone.manager.uploadPendingChanges()

            assertEquals(writes, home.writes)
        }
}
