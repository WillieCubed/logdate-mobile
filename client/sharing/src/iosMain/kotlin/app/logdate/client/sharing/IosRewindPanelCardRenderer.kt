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
import platform.Foundation.writeToFile
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.drawAtPoint

private const val CARD_SIZE = 1080.0
private const val MARGIN = 96.0
private const val TITLE_POINT_SIZE = 64.0
private const val BODY_POINT_SIZE = 40.0
private const val PERIOD_POINT_SIZE = 28.0

/**
 * iOS [RewindPanelCardRenderer] that draws a non-quote panel into a 1080×1080 PNG via UIKit.
 *
 * The card has a hue-derived background, a bold title near the centerline, optional body
 * text beneath it, and a small period label pinned to the bottom margin. All four panel
 * kinds share this layout in v1; per-kind tweaks land later.
 */
class IosRewindPanelCardRenderer : RewindPanelCardRenderer {
    override suspend fun render(panel: RewindPanel): String? =
        withContext(Dispatchers.Main) {
            if (panel.title.isBlank()) return@withContext null
            val image = drawCard(panel) ?: return@withContext null
            val pngData = UIImagePNGRepresentation(image) ?: return@withContext null
            val targetUrl = createImportsUrl(extension = "png") ?: return@withContext null
            val targetPath = targetUrl.path ?: return@withContext null
            val ok = pngData.writeToFile(path = targetPath, atomically = true)
            if (ok) targetUrl.absoluteString else null
        }

    private fun drawCard(panel: RewindPanel): UIImage? {
        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(CARD_SIZE, CARD_SIZE),
            opaque = true,
            scale = 1.0,
        )
        try {
            cardBackground(panel.accentSeed).setFill()
            UIRectFill(CGRectMake(0.0, 0.0, CARD_SIZE, CARD_SIZE))

            val titleAttrs =
                mapOf<Any?, Any>(
                    NSFontAttributeName to UIFont.boldSystemFontOfSize(TITLE_POINT_SIZE),
                    NSForegroundColorAttributeName to UIColor.whiteColor,
                )
            NSString.create(string = panel.title).drawAtPoint(
                point = CGPointMake(MARGIN, CARD_SIZE / 2.0 - TITLE_POINT_SIZE),
                withAttributes = titleAttrs,
            )

            val body = panel.body
            if (!body.isNullOrBlank()) {
                val bodyAttrs =
                    mapOf<Any?, Any>(
                        NSFontAttributeName to UIFont.systemFontOfSize(BODY_POINT_SIZE),
                        NSForegroundColorAttributeName to UIColor.whiteColor.colorWithAlphaComponent(0.85),
                    )
                NSString.create(string = body).drawAtPoint(
                    point = CGPointMake(MARGIN, CARD_SIZE / 2.0 + BODY_POINT_SIZE),
                    withAttributes = bodyAttrs,
                )
            }

            val period = panel.periodLabel
            if (!period.isNullOrBlank()) {
                val periodAttrs =
                    mapOf<Any?, Any>(
                        NSFontAttributeName to UIFont.systemFontOfSize(PERIOD_POINT_SIZE),
                        NSForegroundColorAttributeName to UIColor.whiteColor.colorWithAlphaComponent(0.7),
                    )
                NSString.create(string = period).drawAtPoint(
                    point = CGPointMake(MARGIN, CARD_SIZE - MARGIN - PERIOD_POINT_SIZE),
                    withAttributes = periodAttrs,
                )
            }

            return UIGraphicsGetImageFromCurrentImageContext()
        } catch (t: Throwable) {
            Napier.w("Rewind panel card render failed: ${t.message}")
            return null
        } finally {
            UIGraphicsEndImageContext()
        }
    }
}
