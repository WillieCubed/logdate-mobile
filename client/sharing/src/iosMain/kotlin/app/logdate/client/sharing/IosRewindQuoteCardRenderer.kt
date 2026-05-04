@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.client.sharing

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.Foundation.writeToFile
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.drawAtPoint
import kotlin.math.abs
import kotlin.uuid.Uuid

private const val CARD_SIZE = 1080.0
private const val MARGIN = 96.0
private const val QUOTE_POINT_SIZE = 56.0
private const val DATE_POINT_SIZE = 32.0

/**
 * iOS [RewindQuoteCardRenderer] that draws the quote into a square 1080×1080 PNG using UIKit.
 * Background hue is derived from the quote's accent seed so consecutive cards in a rewind feel
 * visually distinct. Returns a `file://` URI to the saved PNG, or null on failure.
 */
class IosRewindQuoteCardRenderer : RewindQuoteCardRenderer {
    override suspend fun render(quote: RewindQuote): String? =
        withContext(Dispatchers.Main) {
            val image = drawCard(quote) ?: return@withContext null
            val pngData = UIImagePNGRepresentation(image) ?: return@withContext null
            val targetUrl = createImportsUrl(extension = "png") ?: return@withContext null
            val targetPath = targetUrl.path ?: return@withContext null
            val ok = pngData.writeToFile(path = targetPath, atomically = true)
            if (ok) targetUrl.absoluteString else null
        }

    private fun drawCard(quote: RewindQuote): UIImage? {
        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(CARD_SIZE, CARD_SIZE),
            opaque = true,
            scale = 1.0,
        )
        try {
            cardBackground(quote.accentSeed).setFill()
            UIRectFill(CGRectMake(0.0, 0.0, CARD_SIZE, CARD_SIZE))

            val quoteAttrs =
                mapOf<Any?, Any>(
                    NSFontAttributeName to UIFont.boldSystemFontOfSize(QUOTE_POINT_SIZE),
                    NSForegroundColorAttributeName to UIColor.whiteColor,
                )
            val quoteText = NSString.create(string = quote.text)
            quoteText.drawAtPoint(
                point = CGPointMake(MARGIN, CARD_SIZE / 2.0 - QUOTE_POINT_SIZE),
                withAttributes = quoteAttrs,
            )

            val date = quote.dateLabel
            if (!date.isNullOrBlank()) {
                val dateAttrs =
                    mapOf<Any?, Any>(
                        NSFontAttributeName to UIFont.systemFontOfSize(DATE_POINT_SIZE),
                        NSForegroundColorAttributeName to UIColor.whiteColor.colorWithAlphaComponent(0.7),
                    )
                NSString.create(string = date).drawAtPoint(
                    point = CGPointMake(MARGIN, CARD_SIZE - MARGIN - DATE_POINT_SIZE),
                    withAttributes = dateAttrs,
                )
            }

            return UIGraphicsGetImageFromCurrentImageContext()
        } catch (t: Throwable) {
            Napier.w("Rewind quote card render failed: ${t.message}")
            return null
        } finally {
            UIGraphicsEndImageContext()
        }
    }
}

internal fun cardBackground(seed: Int): UIColor {
    val hue = (abs(seed) % 360).toDouble() / 360.0
    return UIColor.colorWithHue(
        hue = hue,
        saturation = 0.50,
        brightness = 0.50,
        alpha = 1.0,
    )
}

internal fun createImportsUrl(extension: String): NSURL? {
    val docs =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: return null
    val importsDir = docs.URLByAppendingPathComponent("imports") ?: return null
    NSFileManager.defaultManager.createDirectoryAtURL(
        url = importsDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return importsDir.URLByAppendingPathComponent("${Uuid.random()}.$extension")
}
