package app.logdate.client.media.audio

import android.content.Context
import androidx.media3.datasource.DataSource

/** Optional platform transport used by the service that owns audio playback. */
fun interface AudioPlaybackSourceFactory {
    fun create(context: Context): DataSource.Factory
}
