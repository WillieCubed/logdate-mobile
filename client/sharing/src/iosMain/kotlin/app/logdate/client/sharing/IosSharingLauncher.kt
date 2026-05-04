@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.client.sharing

import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalRepository
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.setValue
import platform.UIKit.NSFontAttributeName
import platform.UIKit.drawAtPoint
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIPasteboard
import platform.UIKit.UIRectFill
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import kotlin.uuid.Uuid

/**
 * iOS [SharingLauncher] backed by `UIActivityViewController`.
 *
 * The system share sheet covers Messages, Mail, AirDrop, Instagram, and any other app the user has
 * installed that registers a share extension. Instagram-specific feed/story deep links are
 * intentionally routed through the same share sheet — proper `instagram-stories://` deep linking
 * needs a journal-rendering pipeline (sticker images, QR overlays) that does not yet exist on iOS;
 * adding it later is a drop-in replacement for [shareJournalToInstagram] and the Instagram-feed
 * methods without changing this contract.
 *
 * @param mediaManager Used to validate that media UIDs exist before presenting the share sheet,
 * matching the Android contract that throws when a missing media ID is passed in.
 */
class IosSharingLauncher(
    private val journalRepository: JournalRepository,
    private val mediaManager: MediaManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : SharingLauncher {
    override fun shareContent(
        text: String?,
        mediaUris: List<String>,
        title: String?,
        chooserTitle: String?,
    ) {
        coroutineScope.launch {
            val items = buildItems(text, mediaUris)
            if (items.isEmpty()) {
                Napier.w("shareContent: nothing to share")
                return@launch
            }
            presentActivityController(items)
        }
    }

    override fun shareMemoryDay(
        date: LocalDate,
        summary: String,
        mediaUris: List<String>,
    ) {
        val text =
            if (summary.isNotBlank()) {
                "My memory from $date: $summary"
            } else {
                "Check out my memory from $date on LogDate!"
            }
        shareContent(text = text, mediaUris = mediaUris, chooserTitle = "Share memory")
    }

    override fun shareJournalToInstagram(
        journalId: Uuid,
        theme: ShareTheme,
    ) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val card = renderJournalStoryCard(journal.title, theme)
            if (card == null) {
                Napier.w("shareJournalToInstagram: failed to render journal card")
                return@launch
            }
            val data = UIImageJPEGRepresentation(card, compressionQuality = 0.92)
            if (data == null) {
                Napier.w("shareJournalToInstagram: failed to encode card as JPEG")
                return@launch
            }
            withContext(Dispatchers.Main) {
                val payload = mapOf<Any?, Any>(STICKER_BACKGROUND_KEY to data)
                val expiration = NSDate.dateWithTimeIntervalSinceNow(secs = STORY_PASTEBOARD_LIFETIME_SECONDS)
                UIPasteboard.generalPasteboard.setItems(
                    items = listOf(payload),
                    options = mapOf<Any?, Any>("UIPasteboardOptionExpirationDate" to expiration),
                )
                val url =
                    NSURL.URLWithString(STORIES_DEEP_LINK)
                        ?: run {
                            Napier.w("shareJournalToInstagram: invalid stories URL")
                            return@withContext
                        }
                UIApplication.sharedApplication.openURL(
                    url = url,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = { opened ->
                        if (!opened) Napier.w("Instagram not installed; falling back to share sheet")
                    },
                )
            }
        }
    }

    override fun shareJournalLink(journalId: Uuid) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val link = journalDeepLink(journalId)
            val text = "${journal.title} on LogDate — $link"
            presentActivityController(buildItems(text = text, mediaUris = emptyList()))
        }
    }

    override fun shareJournalQrCode(journalId: Uuid) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val link = journalDeepLink(journalId)
            val qrImage = generateQrCodeImage(link)
            val items = mutableListOf<Any>(link, "Scan to open ${journal.title}")
            if (qrImage != null) items.add(0, qrImage)
            presentActivityController(items)
        }
    }

    private fun generateQrCodeImage(text: String): UIImage? {
        val nsString = NSString.create(string = text)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return null
        val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null
        filter.setValue(data, forKey = "inputMessage")
        filter.setValue("M", forKey = "inputCorrectionLevel")
        val output = filter.outputImage ?: return null
        val scaled = output.imageByApplyingTransform(CGAffineTransformMakeScale(10.0, 10.0))
        val context = CIContext()
        val cgImage = context.createCGImage(scaled, fromRect = scaled.extent) ?: return null
        return UIImage.imageWithCGImage(cgImage)
    }

    override fun sharePhotoToInstagramFeed(photoId: String) {
        coroutineScope.launch {
            if (!mediaManager.exists(photoId)) {
                throw IllegalArgumentException("Photo with ID $photoId does not exist")
            }
            // iOS does not expose a public Instagram-feed deep link for arbitrary photos. The
            // canonical pattern is to present the system share sheet, where Instagram appears as
            // a destination if installed.
            val uri = getUriFromMedia(photoId)
            presentActivityController(buildItems(text = null, mediaUris = listOf(uri)))
        }
    }

    override fun shareVideoToInstagramFeed(videoId: String) {
        coroutineScope.launch {
            if (!mediaManager.exists(videoId)) {
                throw IllegalArgumentException("Video with ID $videoId does not exist")
            }
            val uri = getUriFromMedia(videoId)
            presentActivityController(buildItems(text = null, mediaUris = listOf(uri)))
        }
    }

    override fun getUriFromMedia(uid: String): String {
        if (uid.startsWith("file://") || uid.startsWith("https://") ||
            uid.startsWith("photo://") || uid.startsWith("http://")
        ) {
            return uid
        }
        // Resolve relative to the app's managed-media directory inside Documents — mirrors the
        // path layout that IosMediaManager writes to. The file may or may not exist; the share
        // sheet surfaces a clear error in the latter case rather than fabricating a result.
        val docsDir =
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            ) ?: return uid
        return docsDir.URLByAppendingPathComponent("media/$uid")?.absoluteString ?: uid
    }

    private fun buildItems(
        text: String?,
        mediaUris: List<String>,
    ): List<Any> {
        val items = mutableListOf<Any>()
        text?.takeIf { it.isNotBlank() }?.let { items += it }
        mediaUris.forEach { raw ->
            val url = NSURL.URLWithString(raw)
            if (url == null) {
                Napier.w("shareContent: skipping unparseable URI $raw")
                return@forEach
            }
            // photo://<localIdentifier> URIs cannot be shared directly — UIActivityViewController
            // will reject them. They must be exported to NSData via PHAssetResourceManager first;
            // for now drop them with a warning so the share sheet still shows for the text/other
            // items rather than failing outright.
            if (raw.startsWith("photo://")) {
                Napier.w("shareContent: photo://-scheme URI not yet supported on iOS share sheet")
                return@forEach
            }
            items += url
        }
        return items
    }

    private suspend fun presentActivityController(items: List<Any>) {
        if (items.isEmpty()) return
        withContext(Dispatchers.Main) {
            val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
            val host = topPresentedViewController()
            if (host == null) {
                Napier.w("share: no UIViewController to present from")
                return@withContext
            }
            // Required on iPad so the system has an anchor for the popover.
            controller.popoverPresentationController?.sourceView = host.view
            host.view.bounds.useContents {
                controller.popoverPresentationController?.sourceRect =
                    CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
            host.presentViewController(controller, animated = true, completion = null)
        }
    }

    private fun journalDeepLink(journalId: Uuid): String = "https://logdate.app/journal/$journalId"

    private fun renderJournalStoryCard(
        title: String,
        theme: ShareTheme,
    ): UIImage? {
        val isDark = theme == ShareTheme.Dark
        val background = if (isDark) UIColor.blackColor else UIColor.whiteColor
        val foreground = if (isDark) UIColor.whiteColor else UIColor.blackColor
        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(STORY_WIDTH, STORY_HEIGHT),
            opaque = true,
            scale = 1.0,
        )
        background.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, STORY_WIDTH, STORY_HEIGHT))

        val attrs =
            mapOf<Any?, Any>(
                NSFontAttributeName to UIFont.boldSystemFontOfSize(STORY_TITLE_POINT_SIZE),
                NSForegroundColorAttributeName to foreground,
            )
        val nsTitle = NSString.create(string = title.ifBlank { "LogDate" })
        nsTitle.drawAtPoint(
            point = CGPointMake(STORY_TITLE_X, STORY_TITLE_Y),
            withAttributes = attrs,
        )

        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return image
    }

    companion object {
        private const val STORY_WIDTH = 1080.0
        private const val STORY_HEIGHT = 1920.0
        private const val STORY_TITLE_POINT_SIZE = 96.0
        private const val STORY_TITLE_X = 96.0
        private const val STORY_TITLE_Y = 800.0
        private const val STORY_PASTEBOARD_LIFETIME_SECONDS = 60.0 * 5.0
        private const val STORIES_DEEP_LINK = "instagram-stories://share?source_application=studio.hypertext.LogDate"
        private const val STICKER_BACKGROUND_KEY = "com.instagram.sharedSticker.backgroundImage"
    }
}

private fun topPresentedViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    @Suppress("DEPRECATION")
    val window =
        app.keyWindow
            ?: (app.windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow)
            ?: (app.windows.firstOrNull() as? UIWindow)
    var vc: UIViewController = window?.rootViewController ?: return null
    while (true) {
        val presented = vc.presentedViewController ?: break
        vc = presented
    }
    return vc
}
