package app.logdate.client.ambient

import android.content.BroadcastReceiver
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext

internal fun BroadcastReceiver.runAmbientPromptWork(block: suspend (AmbientPromptScheduler) -> Unit) {
    val koin = GlobalContext.getOrNull()
    if (koin == null) {
        Napier.i("Deferring ambient prompt reconciliation until application startup")
        return
    }
    val scheduler = koin.get<AmbientPromptScheduler>()
    val scope = koin.get<CoroutineScope>()
    val pending = goAsync()
    scope
        .launch {
            try {
                withTimeout(8_000) { block(scheduler) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.w("Ambient prompt scheduling will reconcile on the next startup or settings change", error)
            }
        }.invokeOnCompletion {
            // Completion also runs if the application scope was cancelled before the coroutine began.
            pending.finish()
        }
}
