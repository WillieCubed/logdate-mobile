package app.logdate.wear.sync

import app.logdate.client.sync.datalayer.AudioFileDigest
import app.logdate.client.sync.datalayer.audioDigest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Measures a recording so the phone can tell whether the copy it receives is complete. */
fun interface AudioFileDigester {
    /** Null when the file cannot be read; the note then goes without a digest, as notes did before digests existed. */
    suspend fun digest(path: String): AudioFileDigest?
}

/** Reads the whole file on [ioDispatcher]; a 30-minute recording is about 29 MB. */
class FileAudioDigester(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioFileDigester {
    override suspend fun digest(path: String): AudioFileDigest? =
        withContext(ioDispatcher) {
            val file = File(path)
            if (!file.isFile) return@withContext null
            try {
                file.audioDigest()
            } catch (e: IOException) {
                Napier.w("Could not measure $path for the phone; sending the note without a digest", e)
                null
            }
        }
}
