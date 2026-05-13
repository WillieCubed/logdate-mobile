package app.logdate

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Builds the LogDate-configured Coil [ImageLoader] for iOS.
 *
 * Matches the Android configuration: bounded memory and disk caches plus a
 * crossfade default, so timelines, Rewinds, and galleries feel consistent
 * across platforms. The disk cache lives under the app's caches directory
 * (`Library/Caches`), the standard iOS location for purgeable data.
 */
fun buildLogDateImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader
        .Builder(context)
        .crossfade(IMAGE_CROSSFADE_MS)
        .memoryCache {
            MemoryCache
                .Builder()
                .maxSizePercent(context, percent = MEMORY_CACHE_PERCENT)
                .build()
        }.diskCache {
            DiskCache
                .Builder()
                .directory(resolveCachesDirectory().resolve(IMAGE_CACHE_DIR))
                .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                .build()
        }.build()

private fun resolveCachesDirectory(): okio.Path {
    val urls =
        NSFileManager.defaultManager.URLsForDirectory(
            directory = NSCachesDirectory,
            inDomains = NSUserDomainMask,
        )
    val cachesUrl = urls.firstOrNull() as? NSURL
    val path = cachesUrl?.path ?: error("Unable to resolve iOS caches directory")
    return path.toPath()
}

private const val IMAGE_CROSSFADE_MS = 200
private const val MEMORY_CACHE_PERCENT = 0.25
private const val IMAGE_CACHE_DIR = "image_cache"
private const val IMAGE_CACHE_MAX_BYTES = 250L * 1024 * 1024 // 250 MB
