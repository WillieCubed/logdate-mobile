package app.logdate.feature.timeline.ui.marks

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MarkedMomentEditingJourneyTest {
    @Test
    fun `typed caption is durable before entering the editor and returns intact`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val marks = RetainedMarks()
                val editor = MarkedMomentViewModel(marks.value, marks)
                var opened = false
                editor.editCaption("Remember this morning")
                editor.submit({ caption ->
                    assertEquals(caption, marks.value.caption)
                    true
                }, { opened = true })
                advanceUntilIdle()
                assertTrue(opened)
                assertEquals(MarkState.OPEN, marks.value.state)
                assertEquals("Remember this morning", MarkedMomentViewModel(marks.value, marks).state.value.caption)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `attachment failure keeps typed caption available for retry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val marks = RetainedMarks()
                val editor = MarkedMomentViewModel(marks.value, marks)
                editor.editCaption("Keep this caption")
                editor.submit({ throw IllegalStateException("Media unavailable") })
                advanceUntilIdle()
                assertEquals("Keep this caption", editor.state.value.caption)
                assertEquals("Keep this caption", marks.value.caption)
                assertTrue(editor.state.value.failed)
                assertFalse(editor.state.value.busy)
                var received = ""
                editor.submit({
                    received = it
                    true
                })
                advanceUntilIdle()
                assertEquals("Keep this caption", received)
                assertFalse(editor.state.value.failed)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed caption retention blocks navigation and retains input`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val marks = RetainedMarks().also { it.allowSave = false }
                val editor = MarkedMomentViewModel(marks.value, marks)
                var opened = false
                editor.editCaption("Unsaved writing")
                editor.submit({ true }, { opened = true })
                advanceUntilIdle()
                assertFalse(opened)
                assertEquals("Unsaved writing", editor.state.value.caption)
                assertTrue(editor.state.value.failed)
            } finally {
                Dispatchers.resetMain()
            }
        }
}

private class RetainedMarks : MarkedMomentRepository {
    private val at = Instant.parse("2026-05-04T17:35:00Z")
    var value = MarkedMoment(Uuid.random(), at, "America/Los_Angeles", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
    var allowSave = true

    override fun observeOpenMarks() = flowOf(listOf(value))

    override suspend fun getMark(id: Uuid) = value

    override suspend fun updateCaption(
        id: Uuid,
        caption: String,
        updatedAt: Instant,
    ): Boolean {
        if (!allowSave || value.state != MarkState.OPEN) return false
        value = value.copy(caption = caption)
        return true
    }

    override suspend fun dismiss(
        id: Uuid,
        updatedAt: Instant,
    ) = false

    override suspend fun fill(
        id: Uuid,
        caption: String,
        noteIds: Set<Uuid>,
        updatedAt: Instant,
    ) = false

    override suspend fun delete(
        id: Uuid,
        deletedAt: Instant,
    ) = false

    override suspend fun fillLocationIfMissing(
        id: Uuid,
        location: app.logdate.client.repository.moments.MarkLocation,
        updatedAt: Instant,
    ) = false
}
