package app.logdate.wear.sync.v2.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Media3 loader bridge; seeking opens a new pinned range without filling the watch cache. */
@UnstableApi
class ChannelDataSource(
    private val identity: suspend (String) -> WatchMediaIdentity,
    private val source: (WatchMediaIdentity) -> MediaRangeSource,
    private val current: suspend (WatchMediaIdentity) -> Boolean,
) : BaseDataSource(true) {
    private var reader: PhoneMediaReader? = null
    private var openedUri: Uri? = null
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        close()
        transferInitializing(dataSpec)
        return blocking {
            val media = identity(dataSpec.uri.toString())
            val range = PhoneMediaReader(source(media), current)
            reader = range
            val available =
                try {
                    range.open(media, dataSpec.position, dataSpec.length.takeUnless { it == C.LENGTH_UNSET.toLong() })
                } catch (e: PhoneMediaReader.PositionOutOfRange) {
                    throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                }
            openedUri = dataSpec.uri
            started = true
            transferStarted(dataSpec)
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else dataSpec.length
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        blocking {
            val range = reader ?: throw IOException("No phone audio range is open")
            range.read(buffer, offset, length).also { if (it > 0) bytesTransferred(it) }
        }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        val range = reader
        reader = null
        openedUri = null
        try {
            if (range != null) runBlocking(Dispatchers.IO) { range.close() }
        } finally {
            if (started) {
                started = false
                transferEnded()
            }
        }
    }

    private fun <T> blocking(action: suspend () -> T): T =
        try {
            runBlocking(Dispatchers.IO) { action() }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Phone audio loading was interrupted", e)
        } catch (e: CancellationException) {
            throw IOException("Phone audio loading was cancelled", e)
        }
}
