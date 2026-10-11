package app.logdate.client.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import app.logdate.client.sync.datalayer.HealthSnapshotSyncData
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.shared.model.Journal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneDataLayerListenerIngestTest : PhoneDataLayerListenerFixture() {
    @Test
    fun `note sync hands the note to the ingestor`() =
        runTest {
            val note = textNote()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service = serviceWithDependencies(noteIngestor = ingestor)

            service.processEvents(noteEvent(note))

            coVerify(exactly = 1) { ingestor.onNoteMetadata(noteDataMapper.toDataMap(note)) }
        }

    @Test
    fun `note sync survives an undecodable note`() =
        runTest {
            val ingestor = mockk<WatchNoteIngestor>()
            coEvery { ingestor.onNoteMetadata(any()) } throws IllegalArgumentException("no payload")
            val service = serviceWithDependencies(noteIngestor = ingestor)

            service.processEvents(PhoneDataLayerSnapshotEvent(path = NoteDataMapper.notePath(Uuid.random()), data = emptyMap()))

            coVerify(exactly = 1) { ingestor.onNoteMetadata(emptyMap()) }
        }

    @Test
    fun `note delete is handed to the ingestor`() =
        runTest {
            val noteId = Uuid.random()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service = serviceWithDependencies(noteIngestor = ingestor)

            service.processEvents(noteDeleteEvent(noteId))

            coVerify(exactly = 1) { ingestor.onNoteDeleted(noteId) }
        }

    @Test
    fun `invalid note delete path does not reach the ingestor`() =
        runTest {
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service = serviceWithDependencies(noteIngestor = ingestor)

            service.processEvents(PhoneDataLayerSnapshotEvent(path = "/logdate/notes/not-a-uuid/delete", data = emptyMap()))

            coVerify(exactly = 0) { ingestor.onNoteDeleted(any()) }
            confirmVerified(ingestor)
        }

    @Test
    fun `audio channel from the watch streams its bytes to the ingestor and closes`() =
        runTest {
            val noteId = Uuid.random()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val received = mutableListOf<ByteArray>()
            coEvery { ingestor.onAudioBytes(noteId, any()) } coAnswers { received += secondArg<InputStream>().readBytes() }
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                    channelBytes = byteArrayOf(4, 5, 6),
                )

            service.onChannelOpened(channel(WearAudioRequestPaths.audioTransferPath(noteId)))
            advanceUntilIdle()

            assertEquals(1, received.size)
            assertContentEquals(byteArrayOf(4, 5, 6), received.single())
            assertEquals(1, service.closedChannels.size)
        }

    @Test
    fun `channel at an unknown path is ignored`() =
        runTest {
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            service.onChannelOpened(channel("/logdate/other/thing"))
            advanceUntilIdle()

            confirmVerified(ingestor)
            assertTrue(service.closedChannels.isEmpty())
        }

    @Test
    fun `audio channel is closed when the ingestor fails`() =
        runTest {
            val noteId = Uuid.random()
            val ingestor = mockk<WatchNoteIngestor>()
            coEvery { ingestor.onAudioBytes(any(), any()) } throws IllegalStateException("disk full")
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            service.onChannelOpened(channel(WearAudioRequestPaths.audioTransferPath(noteId)))
            advanceUntilIdle()

            assertEquals(1, service.closedChannels.size)
        }

    @Test
    fun `audio channel is closed and dropped when dependencies are unavailable`() =
        runTest {
            val noteId = Uuid.random()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = null)

            service.onChannelOpened(channel(WearAudioRequestPaths.audioTransferPath(noteId)))
            advanceUntilIdle()

            assertEquals(1, service.closedChannels.size)
        }

    @Test
    fun `a journal from the watch goes to the journal ingestor`() =
        runTest {
            val journal = Journal(id = Uuid.random(), title = "Travel")
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(journalEvent(journal))

            coVerify(exactly = 1) { journalIngestor.onJournal(journal) }
        }

    @Test
    fun `a journal deletion from the watch goes to the journal ingestor`() =
        runTest {
            val journalId = Uuid.random()
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(journalDeleteEvent(journalId))

            coVerify(exactly = 1) { journalIngestor.onJournalDeleted(journalId) }
        }

    @Test
    fun `a journal link from the watch goes to the journal ingestor`() =
        runTest {
            val journalId = Uuid.random()
            val contentId = Uuid.random()
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(associationEvent(journalId, contentId))

            coVerify(exactly = 1) { journalIngestor.onAssociation(journalId, contentId) }
        }

    @Test
    fun `a journal link removal from the watch goes to the journal ingestor`() =
        runTest {
            val journalId = Uuid.random()
            val contentId = Uuid.random()
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(associationDeleteEvent(journalId, contentId))

            coVerify(exactly = 1) { journalIngestor.onAssociationDeleted(journalId, contentId) }
        }

    @Test
    fun `an invalid link removal path does not reach the journal ingestor`() =
        runTest {
            val journalIngestor = mockk<WatchJournalIngestor>(relaxed = true)
            val service = serviceWithDependencies(journalIngestor = journalIngestor)

            service.processEvents(PhoneDataLayerSnapshotEvent(path = "/logdate/associations/not-valid/delete", data = emptyMap()))

            confirmVerified(journalIngestor)
        }

    @Test
    fun `health snapshot sync inserts mapped entity`() =
        runTest {
            val snapshot =
                HealthSnapshotSyncData(
                    id = Uuid.random(),
                    noteId = Uuid.random(),
                    heartRateBpm = 72,
                    heartRateVariabilityMs = 18.5f,
                    stepCount = 1234,
                    stressLevel = 0.25f,
                    cumulativeCalories = 44.5f,
                    timestamp = instant("2026-03-31T12:00:00Z"),
                    source = "wear",
                )
            val healthSnapshotDao = mockk<HealthSnapshotDao>(relaxed = true)
            val service = serviceWithDependencies(healthSnapshotDao = healthSnapshotDao)

            service.processEvents(healthSnapshotEvent(snapshot))

            coVerify(exactly = 1) {
                healthSnapshotDao.insert(
                    HealthSnapshotEntity(
                        id = snapshot.id,
                        noteId = snapshot.noteId,
                        heartRateBpm = snapshot.heartRateBpm,
                        heartRateVariabilityMs = snapshot.heartRateVariabilityMs,
                        stepCount = snapshot.stepCount,
                        stressLevel = snapshot.stressLevel,
                        cumulativeCalories = snapshot.cumulativeCalories,
                        timestamp = snapshot.timestamp,
                        source = snapshot.source,
                    ),
                )
            }
        }
}
