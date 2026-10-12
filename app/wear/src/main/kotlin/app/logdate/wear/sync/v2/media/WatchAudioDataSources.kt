package app.logdate.wear.sync.v2.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import app.logdate.client.media.audio.AudioPlaybackSourceFactory

/** Keeps Android file/content playback while routing verified phone sessions over the Data Layer. */
@UnstableApi
class WatchAudioDataSources(
    private val media: WatchPhoneMedia,
) : AudioPlaybackSourceFactory {
    override fun create(context: Context): DataSource.Factory {
        val local = DefaultDataSource.Factory(context)
        return DataSource.Factory {
            RoutedSource(local) { ChannelDataSource(media::identity, media::source, media::isCurrent) }
        }
    }

    private class RoutedSource(
        private val local: DataSource.Factory,
        private val remote: () -> DataSource,
    ) : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var selected: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            if (transferListener !in listeners) listeners += transferListener
            selected?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            close()
            val source = if (dataSpec.uri.toString().startsWith(WatchPhoneMedia.SCHEME)) remote() else local.createDataSource()
            selected = source
            listeners.forEach(source::addTransferListener)
            return source.open(dataSpec)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = checkNotNull(selected).read(buffer, offset, length)

        override fun getUri(): Uri? = selected?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = selected?.responseHeaders.orEmpty()

        override fun close() {
            val source = selected
            selected = null
            source?.close()
        }
    }
}
