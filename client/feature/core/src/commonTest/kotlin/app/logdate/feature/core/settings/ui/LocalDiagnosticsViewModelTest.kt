package app.logdate.feature.core.settings.ui

import app.logdate.client.sync.diagnostics.DiagnosticHistory
import app.logdate.client.sync.diagnostics.DiagnosticReportBundle
import app.logdate.client.sync.diagnostics.DiagnosticStorage
import app.logdate.client.sync.diagnostics.SyncDiagnosticRecorder
import app.logdate.client.sync.diagnostics.VerboseDiagnosticMode
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDiagnosticsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Storage : DiagnosticStorage {
        var value: String? = null

        override suspend fun read() = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    @Test
    fun `preview and export use the same prepared report`() =
        runTest {
            val history = DiagnosticHistory(Storage(), { 1_000L })
            val recorder = SyncDiagnosticRecorder(history, backgroundScope)
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED))
            val exported = mutableListOf<DiagnosticReportBundle>()
            val viewModel =
                LocalDiagnosticsViewModel(
                    recorder,
                    VerboseDiagnosticMode(Storage(), { 1_000L }),
                    object : DiagnosticArchiveExporter {
                        override suspend fun export(bundle: DiagnosticReportBundle): Boolean {
                            exported += bundle
                            return true
                        }
                    },
                )

            viewModel.refresh()
            runCurrent()
            val preview = assertNotNull(viewModel.state.value.preview)
            viewModel.export()
            runCurrent()

            assertEquals(preview, exported.single().entries.getValue("summary.md"))
            assertEquals(1, viewModel.state.value.eventCount)
            assertEquals(LocalDiagnosticsFeedback.EXPORTED, viewModel.state.value.feedback)
        }

    @Test
    fun `clear removes local history without disabling verbose mode`() =
        runTest {
            val history = DiagnosticHistory(Storage(), { 1_000L })
            val recorder = SyncDiagnosticRecorder(history, backgroundScope)
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.APPLY, DiagnosticOutcome.FAILED))
            val mode = VerboseDiagnosticMode(Storage(), { 1_000L })
            mode.enable()
            val viewModel =
                LocalDiagnosticsViewModel(
                    recorder,
                    mode,
                    object : DiagnosticArchiveExporter {
                        override suspend fun export(bundle: DiagnosticReportBundle) = true
                    },
                )

            viewModel.refresh()
            advanceUntilIdle()
            viewModel.clear()
            advanceUntilIdle()

            assertNull(viewModel.state.value.preview)
            assertEquals(0, recorder.report().events.size)
            assertTrue(mode.isEnabled())
            assertEquals(LocalDiagnosticsFeedback.CLEARED, viewModel.state.value.feedback)
        }
}
