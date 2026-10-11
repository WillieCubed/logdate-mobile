package app.logdate.wear.sync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

/** Checks space and cancellation after each read and before writing another chunk to disk. */
internal suspend fun copyAudioWithinBudget(
    input: InputStream,
    output: OutputStream,
    beforeWrite: suspend (Int) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) return
        currentCoroutineContext().ensureActive()
        beforeWrite(count)
        output.write(buffer, 0, count)
    }
}
