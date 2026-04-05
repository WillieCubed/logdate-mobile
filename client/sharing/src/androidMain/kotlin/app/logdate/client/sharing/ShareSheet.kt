package app.logdate.client.sharing

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.chooser.ChooserAction
import app.logdate.shared.model.Journal

internal data class ShareContentRequest(
    val text: String? = null,
    val mediaUris: List<Uri> = emptyList(),
    val title: String? = null,
    val chooserTitle: String? = null,
    val copyText: String? = null,
    val qrCodeImageUri: Uri? = null,
    val qrCodeText: String? = null,
)

/**
 * Shares a journal link using the system share sheet.
 *
 * This presents a system share sheet with some custom share options for the user to choose from,
 * including quick actions on supported Android versions.
 */
internal fun Context.shareJournalLink(
    journal: Journal,
    previewImage: Uri? = null,
    qrCodeImage: Uri? = null,
) {
    val url = "https://logdate.app/j/${journal.id}"
    val mediaUris = listOfNotNull(previewImage)
    shareContent(
        request =
            ShareContentRequest(
                text = url,
                mediaUris = mediaUris,
                title = journal.title,
                chooserTitle = "Share journal",
                copyText = url,
                qrCodeImageUri = qrCodeImage,
                qrCodeText = url,
            ),
    )
}

internal fun Context.shareJournalQrCode(
    journal: Journal,
    qrCodeImage: Uri,
) {
    val url = "https://logdate.app/j/${journal.id}"
    shareContent(
        request =
            ShareContentRequest(
                text = url,
                mediaUris = listOf(qrCodeImage),
                title = journal.title,
                chooserTitle = "Share QR code",
                copyText = url,
            ),
    )
}

/**
 * Creates a chooser intent for sharing content, the system "share sheet".
 */
internal fun Context.shareContent(request: ShareContentRequest) {
    val shareIntent = buildShareIntent(request)
    val callbackIntent =
        Intent(this, ShareReceiver::class.java).apply {
            action = CustomIntents.ACTION_CHOOSER_RESULT
        }
    val pendingIntent =
        PendingIntent.getBroadcast(
            this,
            0,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    val chooserIntent =
        Intent
            .createChooser(
                shareIntent,
                request.chooserTitle,
                pendingIntent.intentSender,
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, buildChooserActions(request))
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    startActivity(chooserIntent)
}

private fun Context.buildShareIntent(request: ShareContentRequest): Intent {
    val shareIntent =
        when (request.mediaUris.size) {
            0 ->
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                }
            1 ->
                Intent(Intent.ACTION_SEND).apply {
                    type = resolveMimeType(request.mediaUris)
                    putExtra(Intent.EXTRA_STREAM, request.mediaUris.first())
                    clipData = ClipData.newUri(contentResolver, request.title ?: "shared media", request.mediaUris.first())
                }
            else ->
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = resolveMimeType(request.mediaUris)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(request.mediaUris))
                    clipData =
                        ClipData
                            .newUri(
                                contentResolver,
                                request.title ?: "shared media",
                                request.mediaUris.first(),
                            ).apply {
                                request.mediaUris.drop(1).forEach { uri ->
                                    addItem(ClipData.Item(uri))
                                }
                            }
                }
        }

    return shareIntent.apply {
        request.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        request.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun Context.resolveMimeType(mediaUris: List<Uri>): String {
    val mimeTypes = mediaUris.mapNotNull(contentResolver::getType).distinct()
    if (mimeTypes.isEmpty()) return "*/*"
    if (mimeTypes.size == 1) return mimeTypes.first()
    val topLevelTypes = mimeTypes.map { it.substringBefore('/') }.distinct()
    return if (topLevelTypes.size == 1) "${topLevelTypes.first()}/*" else "*/*"
}

private fun Context.buildChooserActions(request: ShareContentRequest): Array<ChooserAction> {
    val actions = mutableListOf<ChooserAction>()
    request.copyText
        ?.takeIf(String::isNotBlank)
        ?.let { copyText ->
            actions +=
                ChooserAction
                    .Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_set_as),
                        "Copy link",
                        PendingIntent.getBroadcast(
                            this,
                            1,
                            Intent(this, ShareReceiver::class.java).apply {
                                action = CustomIntents.ACTION_COPY_LINK
                                putExtra(CustomIntents.EXTRA_SHARE_TEXT, copyText)
                            },
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
                        ),
                    ).build()
        }
    val qrCodeImageUri = request.qrCodeImageUri
    val qrCodeText = request.qrCodeText
    if (qrCodeImageUri != null && !qrCodeText.isNullOrBlank()) {
        actions +=
            ChooserAction
                .Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_share),
                    "Share QR code",
                    PendingIntent.getBroadcast(
                        this,
                        2,
                        Intent(this, ShareReceiver::class.java).apply {
                            action = CustomIntents.ACTION_SHARE_QR_CODE
                            putExtra(CustomIntents.EXTRA_SHARE_TEXT, qrCodeText)
                            putExtra(CustomIntents.EXTRA_SHARE_URI, qrCodeImageUri)
                            putExtra(CustomIntents.EXTRA_SHARE_TITLE, request.title)
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
                    ),
                ).build()
    }
    return actions.toTypedArray()
}

/**
 * Custom intent actions and extras used by the share sheet.
 */
object CustomIntents {
    const val EXTRA_SHARE_TEXT: String = "app.mobile.logdate.extra.SHARE_TEXT"
    const val EXTRA_SHARE_TITLE: String = "app.mobile.logdate.extra.SHARE_TITLE"
    const val EXTRA_SHARE_URI: String = "app.mobile.logdate.extra.SHARE_URI"

    const val ACTION_CHOOSER_RESULT: String = "app.mobile.logdate.action.CHOOSER_RESULT"

    const val ACTION_COPY_LINK: String = "app.mobile.logdate.action.COPY_LINK"
    const val ACTION_SHARE_QR_CODE: String = "app.mobile.logdate.action.SHARE_QR_CODE"
}
