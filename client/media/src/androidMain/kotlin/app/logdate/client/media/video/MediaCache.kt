package app.logdate.client.media.video

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-singleton video cache that lets ExoPlayer replay or scrub recently
 * watched media without re-fetching from upstream content providers
 * (e.g. cloud-backed URIs handed back by the photo picker). The cache is
 * bounded by an LRU evictor so it can live for the app's lifetime without
 * growing unbounded.
 *
 * Only one [SimpleCache] instance may ever point at a given directory — this
 * class is the single owner, scoped via Koin in [app.logdate.client.media.di.mediaModule].
 */
@UnstableApi
class MediaCache(
    context: Context,
) {
    private val cacheDirectory: File =
        File(context.cacheDir, MEDIA_CACHE_DIR).apply { mkdirs() }

    private val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)

    private val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)

    private val simpleCache: SimpleCache =
        SimpleCache(cacheDirectory, evictor, databaseProvider)

    /**
     * Builds a [DataSource.Factory] that reads through the disk cache and
     * falls back to [upstreamFactory] (defaulting to [DefaultDataSource]) on
     * cache miss. Use this anywhere a [DataSource.Factory] is wired into
     * Media3 (`ExoPlayer.Builder.setMediaSourceFactory`, etc.).
     */
    fun dataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(context),
    ): DataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            // Surface upstream errors instead of swallowing them as cache misses;
            // a permanent failure should reach the player so it can stop trying.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Releases the underlying cache. Not called in normal app lifecycle — the
     * cache is process-lifetime — but exposed for instrumentation and tests
     * that need to reset the cache directory between runs.
     */
    fun release() {
        simpleCache.release()
    }

    companion object {
        private const val MEDIA_CACHE_DIR = "media_cache"

        // 200 MB strikes a balance between letting users re-watch / scrub a
        // recently picked video instantly and not eating into long-term
        // device storage. Mirrors the image cache budget (250 MB).
        private const val MAX_CACHE_BYTES = 200L * 1024 * 1024
    }
}
