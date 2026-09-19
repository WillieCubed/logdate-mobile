package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.MediaSourceOpener
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Source
import okio.source
import java.io.File
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * Opens the media references the desktop app stores: absolute paths and `file:` URLs. Anything else,
 * including a network address, is treated as missing, since an export never fetches from the network.
 */
class DesktopMediaSourceOpener : MediaSourceOpener {
    override suspend fun open(reference: String): Source? =
        withContext(Dispatchers.IO) {
            val file = fileFor(reference)
            file?.takeIf { it.isFile }?.source()
        }

    private fun fileFor(reference: String): File? {
        if (reference.startsWith("/")) return File(reference)
        return try {
            val uri = URI(reference)
            if (uri.scheme == "file") File(uri) else null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Napier.w("A media reference could not be read for export", failure)
            null
        }
    }
}
