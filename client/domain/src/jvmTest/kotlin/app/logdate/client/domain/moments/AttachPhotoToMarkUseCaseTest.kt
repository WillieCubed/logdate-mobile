package app.logdate.client.domain.moments

import app.logdate.client.media.ManagedMediaImporter
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AttachPhotoToMarkUseCaseTest {
    private val id = Uuid.random()
    private val at = Instant.parse("2026-10-12T12:00:00Z")
    private val mark = MarkedMoment(id, at, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
    private val marks = mockk<MarkedMomentRepository>()
    private val notes = mockk<JournalNotesRepository>()
    private val importer = mockk<ManagedMediaImporter>()
    private val transaction =
        object : SyncTransactionManager {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        }

    @Test
    fun `attachment imports before saving and fills with saved note`() =
        runTest {
            coEvery { marks.getMark(id) } returns mark
            coEvery { importer.import("gallery") } returns "managed"
            coEvery { notes.create(any()) } answers { firstArg<app.logdate.client.repository.journals.JournalNote>().uid }
            coEvery { marks.fill(id, "", any(), any()) } returns true
            assertTrue(AttachPhotoToMarkUseCase(marks, notes, importer, transaction)(id, "gallery"))
            coVerify {
                notes.create(
                    match {
                        it is app.logdate.client.repository.journals.JournalNote.Image &&
                            it.mediaRef == "managed" &&
                            it.creationTimestamp == at
                    },
                )
            }
        }

    @Test
    fun `deleted mark never imports or creates photo`() =
        runTest {
            coEvery { marks.getMark(id) } returns null
            assertFalse(AttachPhotoToMarkUseCase(marks, notes, importer, transaction)(id, "gallery"))
            coVerify(exactly = 0) { importer.import(any()) }
        }

    @Test
    fun `caption remains visible as a timeline note after mark becomes filled`() =
        runTest {
            coEvery { marks.getMark(id) } returns mark
            coEvery { notes.create(any()) } answers { firstArg<app.logdate.client.repository.journals.JournalNote>().uid }
            coEvery { marks.fill(id, "Good morning", any(), any()) } returns true
            assertTrue(SaveMarkCaptionUseCase(marks, notes, transaction)(id, "  Good morning  "))
            coVerify {
                notes.create(
                    match {
                        it is app.logdate.client.repository.journals.JournalNote.Text &&
                            it.content == "Good morning" &&
                            it.creationTimestamp == at
                    },
                )
            }
        }

    @Test
    fun `attaching a photo publishes retained caption with original place and time`() =
        runTest {
            val context = mark.copy(caption = "Morning by the lake", location = MarkLocation(37.7, -122.4, 8f))
            val saved = mutableListOf<JournalNote>()
            coEvery { marks.getMark(id) } returns context
            coEvery { importer.import("gallery") } returns "managed"
            coEvery { notes.create(any()) } answers { firstArg<JournalNote>().also(saved::add).uid }
            coEvery { marks.fill(id, context.caption, any(), any()) } returns true
            assertTrue(AttachPhotoToMarkUseCase(marks, notes, importer, transaction)(id, "gallery"))
            assertEquals("Morning by the lake", saved.filterIsInstance<JournalNote.Text>().single().content)
            assertEquals(2, saved.size)
            saved.forEach {
                assertEquals(at, it.creationTimestamp)
                assertEquals("UTC", it.timeZoneId)
                assertEquals(37.7, it.location?.coordinates?.latitude)
            }
        }

    @Test
    fun `saving a caption retains original place instead of requesting current location`() =
        runTest {
            val context = mark.copy(location = MarkLocation(37.7, -122.4, 8f))
            val saved = mutableListOf<JournalNote>()
            coEvery { marks.getMark(id) } returns context
            coEvery { notes.create(any()) } answers { firstArg<JournalNote>().also(saved::add).uid }
            coEvery { marks.fill(id, "Morning", any(), any()) } returns true
            assertTrue(SaveMarkCaptionUseCase(marks, notes, transaction)(id, "Morning"))
            assertEquals(
                37.7,
                saved
                    .single()
                    .location
                    ?.coordinates
                    ?.latitude,
            )
        }
}
