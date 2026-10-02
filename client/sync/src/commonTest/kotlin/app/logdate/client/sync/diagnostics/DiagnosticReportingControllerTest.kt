package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.PrivacyEpochStorage
import app.logdate.shared.config.PrivacyScopeEpoch
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiagnosticReportingControllerTest {
    private class Storage :
        DiagnosticStorage,
        PrivacyEpochStorage {
        var value: String? = null

        override suspend fun read() = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    private val owner = UploadScope("owner-a", "https://private-a.invalid")
    private val failed = SyncDiagnosticEvent(DiagnosticPhase.APPLY, DiagnosticOutcome.FAILED)

    @Test
    fun `off and preconsent queued events stay local while new consent permits only new failures`() =
        runTest {
            val epoch = PrivacyScopeEpoch(Storage())
            epoch.initialize()
            var sends = 0
            val controller =
                DiagnosticReportingController(
                    Storage(),
                    epoch,
                    backgroundScope,
                    { owner },
                    { true },
                    { 1000L },
                    {},
                    { _, _ ->
                        sends++
                        DiagnosticDelivery.ACCEPTED
                    },
                    { true },
                )
            controller.refresh()
            val source = DiagnosticSource(owner, epoch.value.value)
            controller.record(failed, source)
            val consent = assertNotNull(controller.prepareConsent())
            assertEquals(owner.serverOrigin, consent.destination)
            controller.enable(consent)
            runCurrent()
            assertEquals(0, sends)
            controller.record(failed, source)
            runCurrent()
            assertEquals(1, sends)
            controller.disable()
            controller.record(failed, source)
            runCurrent()
            assertFalse(controller.state.value.enabled)
            assertEquals(1, sends)
        }

    @Test
    fun `unsupported servers never offer consent or send reports`() =
        runTest {
            val epoch = PrivacyScopeEpoch(Storage())
            var sends = 0
            val controller =
                DiagnosticReportingController(
                    Storage(),
                    epoch,
                    backgroundScope,
                    { owner },
                    { false },
                    { 1000L },
                    {},
                    { _, _ ->
                        sends++
                        DiagnosticDelivery.ACCEPTED
                    },
                    { true },
                )
            controller.refresh()
            assertNull(controller.prepareConsent())
            controller.record(failed, DiagnosticSource(owner, epoch.value.value))
            runCurrent()
            assertEquals(0, sends)
            assertFalse(controller.state.value.available)
        }
}
