package app.logdate.wear.launch

import android.content.Context
import app.logdate.wear.LogDateWearApplication

internal fun Context.isWearStartupReady(): Boolean =
    (applicationContext as? LogDateWearApplication)?.startup?.state?.value is WearStartupState.Ready

internal suspend fun Context.awaitWearStartup(): Boolean = (applicationContext as? LogDateWearApplication)?.startup?.awaitReady() ?: false

internal suspend fun Context.awaitWearStartupWithin(timeoutMs: Long): Boolean =
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { awaitWearStartup() } == true

/** Bounds remote-command readiness so a timed-out handoff cannot start playback much later. */
internal suspend fun Context.resolveWearServiceKoin(readinessTimeoutMs: Long = 1_000L): org.koin.core.Koin? {
    if (!awaitWearStartupWithin(readinessTimeoutMs)) return null
    return try {
        org.koin.java.KoinJavaComponent
            .getKoin()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        io.github.aakira.napier.Napier
            .w("Watch dependencies are unavailable", error)
        null
    }
}
