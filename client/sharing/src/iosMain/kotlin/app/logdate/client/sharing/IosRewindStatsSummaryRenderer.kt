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
import platform.Foundation.NSString
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

private const val CARD_SIZE = 1080.0
private const val MARGIN = 96.0
private const val TITLE_POINT_SIZE = 72.0
private const val SUBTITLE_POINT_SIZE = 32.0
private const val COUNTER_VALUE_POINT_SIZE = 96.0
private const val COUNTER_LABEL_POINT_SIZE = 28.0
private const val HIGHLIGHT_POINT_SIZE = 28.0
private const val SECTION_GAP = 64.0

/**
 * iOS [RewindStatsSummaryRenderer] that draws the year/period stats into a 1080×1080 PNG.
 * Layout: title and subtitle at the top, a row of big-number counters in the middle, and a
 * stack of "heading: value" highlights at the bottom. Returns a `file://` URI to the saved
 * PNG, or null on failure.
 */
class IosRewindStatsSummaryRenderer : RewindStatsSummaryRenderer {
    override suspend fun render(summary: RewindStatsSummary): String? =
        withContext(Dispatchers.Main) {
            val image = drawCard(summary) ?: return@withContext null
            val pngData = UIImagePNGRepresentation(image) ?: return@withContext null
            val targetUrl = createImportsUrl(extension = "png") ?: return@withContext null
            val targetPath = targetUrl.path ?: return@withContext null
            val ok = pngData.writeToFile(path = targetPath, atomically = true)
            if (ok) targetUrl.absoluteString else null
        }

    private fun drawCard(summary: RewindStatsSummary): UIImage? {
        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(CARD_SIZE, CARD_SIZE),
            opaque = true,
            scale = 1.0,
        )
        try {
            cardBackground(summary.accentSeed).setFill()
            UIRectFill(CGRectMake(0.0, 0.0, CARD_SIZE, CARD_SIZE))

            val titleAttrs =
                mapOf<Any?, Any>(
                    NSFontAttributeName to UIFont.boldSystemFontOfSize(TITLE_POINT_SIZE),
                    NSForegroundColorAttributeName to UIColor.whiteColor,
                )
            NSString.create(string = summary.title).drawAtPoint(
                point = CGPointMake(MARGIN, MARGIN),
                withAttributes = titleAttrs,
            )

            val subtitleAttrs =
                mapOf<Any?, Any>(
                    NSFontAttributeName to UIFont.systemFontOfSize(SUBTITLE_POINT_SIZE),
                    NSForegroundColorAttributeName to UIColor.whiteColor.colorWithAlphaComponent(0.7),
                )
            NSString.create(string = summary.subtitle).drawAtPoint(
                point = CGPointMake(MARGIN, MARGIN + TITLE_POINT_SIZE + 8.0),
                withAttributes = subtitleAttrs,
            )

            val countersTop = MARGIN + TITLE_POINT_SIZE + SUBTITLE_POINT_SIZE + SECTION_GAP
            val visibleCounters = summary.counters.take(MAX_COUNTERS)
            if (visibleCounters.isNotEmpty()) {
                val columnWidth = (CARD_SIZE - 2 * MARGIN) / visibleCounters.size
                val valueAttrs =
                    mapOf<Any?, Any>(
                        NSFontAttributeName to UIFont.boldSystemFontOfSize(COUNTER_VALUE_POINT_SIZE),
                        NSForegroundColorAttributeName to UIColor.whiteColor,
                    )
                val labelAttrs =
                    mapOf<Any?, Any>(
                        NSFontAttributeName to UIFont.systemFontOfSize(COUNTER_LABEL_POINT_SIZE),
                        NSForegroundColorAttributeName to UIColor.whiteColor.colorWithAlphaComponent(0.7),
                    )
                visibleCounters.forEachIndexed { index, counter ->
                    val columnLeft = MARGIN + columnWidth * index
                    NSString.create(string = counter.count.toString()).drawAtPoint(
                        point = CGPointMake(columnLeft, countersTop),
                        withAttributes = valueAttrs,
                    )
                    NSString.create(string = counter.label).drawAtPoint(
                        point = CGPointMake(columnLeft, countersTop + COUNTER_VALUE_POINT_SIZE + 8.0),
                        withAttributes = labelAttrs,
                    )
                }
            }

            var highlightTop = countersTop + COUNTER_VALUE_POINT_SIZE + COUNTER_LABEL_POINT_SIZE + SECTION_GAP
            val highlightAttrs =
                mapOf<Any?, Any>(
                    NSFontAttributeName to UIFont.systemFontOfSize(HIGHLIGHT_POINT_SIZE),
                    NSForegroundColorAttributeName to UIColor.whiteColor.colorWithAlphaComponent(0.9),
                )
            summary.highlights.take(MAX_HIGHLIGHTS).forEach { highlight ->
                NSString.create(string = "${highlight.heading}: ${highlight.value}").drawAtPoint(
                    point = CGPointMake(MARGIN, highlightTop),
                    withAttributes = highlightAttrs,
                )
                highlightTop += HIGHLIGHT_POINT_SIZE + 16.0
            }

            return UIGraphicsGetImageFromCurrentImageContext()
        } catch (t: Throwable) {
            Napier.w("Rewind stats summary render failed: ${t.message}")
            return null
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    private companion object {
        const val MAX_COUNTERS = 4
        const val MAX_HIGHLIGHTS = 3
    }
}
