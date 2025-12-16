package app.logdate.client.sharing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.chooser.ChooserAction
import androidx.annotation.RequiresApi
import app.logdate.shared.model.Journal
import logdate.client.sharing.generated.resources.Res
import logdate.client.sharing.generated.resources.action_label_add_to_journal
import logdate.client.sharing.generated.resources.rounded_add_24
import kotlin.uuid.Uuid

/**
 * Shares a journal link using the system share sheet.
 *
 * This presents a system share sheet with some custom share options for the user to choose from,
 * including a "Share QR code" option that generates a QR code for the journal.
 */
internal fun Context.shareJournalLink(
    journal: Journal,
    previewImage: Uri? = null,
) = Intent().apply {
    action = Intent.ACTION_SEND
    // TODO: Use a custom URL shortener
    // TODO: Move URL generation to a utility function
    val url = "https://logdate.app/j/${journal.id}"
    putExtra(Intent.EXTRA_TEXT, url)
    putExtra(Intent.EXTRA_TITLE, journal.title)
    if (previewImage != null) {
        data = previewImage
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        putExtra(Intent.EXTRA_CHOOSER_TARGETS, getJournalShareCustomOptions(journal.id))
    }
}.run {
    val pendingIntent = PendingIntent.getBroadcast(
        this@shareJournalLink,
        0,
        Intent(this@shareJournalLink, ShareReceiver::class.java),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    Intent.createChooser(
        this,
        // TODO: Use string resource from Compose resources
        "Share journal",
//        getString(Res.string.share_title_share_journal),
        pendingIntent.intentSender,
    )
}.also {
    startActivity(it)
}

/**
 * Creates a chooser intent for sharing content, the system "share sheet".
 * @param title The title of the chooser dialog, only shown if
 */
internal fun createChooserIntent(
    context: Context,
    title: String = "Share with",
    image: Uri? = null,
) = Intent(Intent.ACTION_ANSWER).apply {
    action = Intent.ACTION_SEND
    data = image
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        putExtra(Intent.EXTRA_CHOOSER_TARGETS, context.getNoteShareCustomOptions())
    }
}.run {
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ShareReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
    )
    Intent.createChooser(this, title, pendingIntent.intentSender)
}

/**
 * Creates a chooser intent for sharing content, the system "share sheet".
 */
internal fun createChooserIntent(
    images: List<Uri>,
) = Intent().apply {
    action = Intent.ACTION_SEND_MULTIPLE
    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(images))
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun Context.getJournalShareCustomOptions(
    journalId: Uuid,
): Array<ChooserAction> {
    return arrayOf(
        ChooserAction.Builder(
            // TODO: Use custom app icons
            Icon.createWithResource(this, R.drawable.rounded_qr_code_24),
            "Share QR code",
//            getString(Res.string.action_label_share_qr_code),
            PendingIntent.getBroadcast(
                this,
                1,
                Intent(CustomIntents.ACTION_GENERATE_QR_CODE).apply {
                    putExtra(CustomIntents.EXTRA_JOURNAL_ID, journalId.toString())
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
        ).build(),
    )
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun Context.getNoteShareCustomOptions(
): Array<ChooserAction> {
    return arrayOf(
        // TODO: Use custom app icons
        ChooserAction.Builder(
            Icon.createWithResource(this, R.drawable.rounded_add_24),
            "Add to journal",
//            getString(Res.string.action_label_add_to_journal),
            PendingIntent.getBroadcast(
                this,
                1,
                Intent(CustomIntents.ACTION_ADD_TO_JOURNAL).apply {},
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
        ).build(),
        ChooserAction.Builder(
            Icon.createWithResource(this, android.R.drawable.star_on),
//            getString(Res.string.action_label_add_to_journal),
            "Add to journal",
            PendingIntent.getBroadcast(
                this,
                1,
                Intent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
        ).build(),
    )
}

/**
 * Custom intent actions and extras used by the share sheet.
 */
object CustomIntents {
    /**
     * The UID of the journal to generate a QR code for.
     */
    const val EXTRA_JOURNAL_ID: String = "app.mobile.logdate.extra.JOURNAL_ID"

    /**
     * Action to generate a QR code for a journal.
     */
    const val ACTION_ADD_TO_JOURNAL: String = "app.mobile.logdate.action.ADD_TO_JOURNAL"

    /**
     * Action to generate a QR code for a journal.
     */
    const val ACTION_GENERATE_QR_CODE: String = "app.mobile.logdate.action.GENERATE_QR_CODE"
}
