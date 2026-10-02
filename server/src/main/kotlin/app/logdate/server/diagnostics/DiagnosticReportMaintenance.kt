package app.logdate.server.diagnostics

import io.github.aakira.napier.Napier
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

private const val CLEANUP_INTERVAL_MS = 60 * 60 * 1_000L

fun Application.installDiagnosticReportMaintenance() {
    val availability by inject<DiagnosticReportAvailability>()
    if (!availability.enabled) return
    val store by inject<DiagnosticReportStore>()
    val keyring = availability.keyring ?: return
    val service = DiagnosticReportService(store, keyring)
    val job =
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (isActive) {
                try {
                    service.purgeExpired()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Napier.w("Diagnostic report cleanup failed")
                }
                delay(CLEANUP_INTERVAL_MS)
            }
        }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
