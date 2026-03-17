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
        val mode = dataUsagePolicy.currentMode()
        val maxSize =
            when (mode) {
                is DataUsageMode.Restricted -> MAX_SIZE_RESTRICTED
                is DataUsageMode.Conservative -> MAX_SIZE_CONSERVATIVE
                is DataUsageMode.Unrestricted -> return chain.proceed()
            }

        val request = chain.request
        val constrainedSize = chain.size.constrainTo(maxSize)
        if (constrainedSize == chain.size) {
            return chain.proceed()
        }

        Napier.d("DataSaverImageInterceptor: Constraining image to ${maxSize}px (mode=$mode)")
        return chain.withSize(constrainedSize).proceed()
    }

    companion object {
        private const val MAX_SIZE_RESTRICTED = 480
        private const val MAX_SIZE_CONSERVATIVE = 1080
    }
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
