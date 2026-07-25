package app.logdate.client.image

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size
import io.github.aakira.napier.Napier
import kotlin.math.roundToInt

/**
 * Coil interceptor that adjusts image request parameters based on the current
 * [DataUsagePolicy].
 *
 * - [DataUsageMode.Restricted]: Caps resolution to [MAX_SIZE_RESTRICTED] px.
 * - [DataUsageMode.Conservative]: Caps resolution to [MAX_SIZE_CONSERVATIVE] px.
 * - [DataUsageMode.Unrestricted]: Passes through unchanged.
 *
 * Images are always fetched (never blocked) to avoid degraded UX with missing content.
 * Reduced resolution minimizes transfer size while keeping images visible.
 */
class DataSaverImageInterceptor(
    private val dataUsagePolicy: DataUsagePolicy,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // Data Saver exists to cut network transfer, not to degrade files that already
        // live on-device — capping those buys nothing and just makes local photos and
        // videos look pixelated when rendered full-screen (e.g. in Rewind).
        if (chain.request.data.isLocalMediaSource()) {
            return chain.proceed()
        }

        val mode = dataUsagePolicy.currentMode()
        val maxSize =
            when (mode) {
                is DataUsageMode.Restricted -> MAX_SIZE_RESTRICTED
                is DataUsageMode.Conservative -> MAX_SIZE_CONSERVATIVE
                is DataUsageMode.Unrestricted -> return chain.proceed()
            }

        val constrainedSize = chain.size.constrainTo(maxSize)
        if (constrainedSize == chain.size) {
            return chain.proceed()
        }

        Napier.d("DataSaverImageInterceptor: Constraining image to ${maxSize}px (mode=$mode)")
        return chain.withSize(constrainedSize).proceed()
    }

    companion object {
        // Sized against modern phone screens rather than thumbnails: even the restricted
        // ceiling should look sharp when a remote image fills most of the display, not just
        // "visible." Both are still well below typical camera-original resolution, so the
        // transfer savings versus [DataUsageMode.Unrestricted] remain substantial.
        private const val MAX_SIZE_RESTRICTED = 960
        private const val MAX_SIZE_CONSERVATIVE = 1600
    }
}

private val LOCAL_URI_SCHEMES = listOf("content://", "file://", "android.resource://")

private fun Any?.isLocalMediaSource(): Boolean {
    val uriString = this?.toString() ?: return false
    return LOCAL_URI_SCHEMES.any { uriString.startsWith(it) }
}

private fun Size.constrainTo(maxDimension: Int): Size {
    val w = width
    val h = height
    if (w !is Dimension.Pixels && h !is Dimension.Pixels) {
        return Size(maxDimension, maxDimension)
    }
    val wPx = (w as? Dimension.Pixels)?.px ?: return this
    val hPx = (h as? Dimension.Pixels)?.px ?: return this
    val maxCurrent = maxOf(wPx, hPx)
    if (maxCurrent <= maxDimension) return this
    val scale = maxDimension.toFloat() / maxCurrent
    return Size(
        (wPx * scale).roundToInt(),
        (hPx * scale).roundToInt(),
    )
}
