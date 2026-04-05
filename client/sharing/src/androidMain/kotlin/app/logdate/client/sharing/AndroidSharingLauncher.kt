package app.logdate.client.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * A utility that handles sharing content to external apps.
 */
class AndroidSharingLauncher(
    private val context: Context,
    private val mediaManager: MediaManager,
    private val journalRepository: JournalRepository,
    private val shareAssetGenerator: ShareAssetInterface,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : SharingLauncher {
    override fun shareContent(
        text: String?,
        mediaUris: List<String>,
        title: String?,
        chooserTitle: String?,
    ) {
        val resolvedUris = mediaUris.map(::getUriFromMedia)
        context.shareContent(
            request =
                ShareContentRequest(
                    text = text,
                    mediaUris = resolvedUris,
                    title = title,
                    chooserTitle = chooserTitle,
                ),
        )
    }

    override fun shareMemoryDay(
        date: LocalDate,
        summary: String,
        mediaUris: List<String>,
    ) {
        val shareText =
            if (summary.isNotBlank()) {
                "My memory from $date: $summary"
            } else {
                "Check out my memory from $date on LogDate!"
            }
        shareContent(
            text = shareText,
            mediaUris = mediaUris,
            chooserTitle = "Share memory",
        )
    }

    /**
     * Shares a journal to Instagram as a story.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     * @param theme The theme to use for the shared content
     */
    override fun shareJournalToInstagram(
        journalId: Uuid,
        theme: ShareTheme,
    ) {
        coroutineScope.launch {
            if (!isInstagramInstalled()) {
                Napier.w("Instagram is not installed; cannot share journal $journalId")
                return@launch
            }
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: run {
                        Napier.e("Journal $journalId not found; cannot share to Instagram")
                        return@launch
                    }
            try {
                val coverUri = Uri.parse(shareAssetGenerator.generateStickerLayer(journal))
                context.grantUriPermission(
                    INSTAGRAM_PACKAGE_NAME,
                    coverUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                val bgHex = journalBackgroundHex(journal, theme)
                context.startActivity(
                    createInstagramStoryIntent(coverUri, bgHex, bgHex).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (e: ActivityNotFoundException) {
                // Instagram could be uninstalled between isInstagramInstalled() and startActivity()
                Napier.e("Instagram activity not found when sharing journal $journalId", e)
            } catch (e: Exception) {
                Napier.e("Failed to share journal $journalId to Instagram", e)
            }
        }
    }

    private fun isInstagramInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo(INSTAGRAM_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun journalBackgroundHex(
        journal: Journal,
        theme: ShareTheme,
    ): String {
        val hue = abs(journal.id.hashCode() % HUE_DEGREES).toFloat()
        val colorInt =
            if (theme == ShareTheme.Dark) {
                ColorUtils.HSLToColor(floatArrayOf(hue, BG_DARK_SATURATION, BG_DARK_LIGHTNESS))
            } else {
                ColorUtils.HSLToColor(floatArrayOf(hue, BG_LIGHT_SATURATION, BG_LIGHT_LIGHTNESS))
            }
        return String.format("#%06X", RGB_MASK and colorInt)
    }

    companion object {
        private const val HUE_DEGREES = 360
        private const val BG_LIGHT_SATURATION = 0.20f
        private const val BG_LIGHT_LIGHTNESS = 0.92f
        private const val BG_DARK_SATURATION = 0.15f
        private const val BG_DARK_LIGHTNESS = 0.12f
        private const val RGB_MASK = 0xFFFFFF
    }

    /**
     * Shares a journal using the system share sheet.
     *
     * @param journalId The ID of the journal to share. If the journal does not exist, an exception
     * will be thrown.
     */
    override fun shareJournalLink(journalId: Uuid) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val previewUri = Uri.parse(shareAssetGenerator.generateStickerLayer(journal))
            val qrCodeUri = Uri.parse(shareAssetGenerator.generateJournalQrCode(journal))
            context.shareJournalLink(journal, previewUri, qrCodeUri)
        }
    }

    override fun shareJournalQrCode(journalId: Uuid) {
        coroutineScope.launch {
            val journal =
                journalRepository.observeJournalById(journalId).firstOrNull()
                    ?: throw IllegalArgumentException("Journal with ID $journalId does not exist")
            val qrCodeUri = Uri.parse(shareAssetGenerator.generateJournalQrCode(journal))
            context.shareJournalQrCode(journal, qrCodeUri)
        }
    }

    /**
     * Triggers the app to share a photo to Instagram.
     *
     * This method will trigger the Instagram app to open and share the photo. The given photo ID
     * must exist. If the image does not exist, an exception will be thrown.
     *
     * @param photoId The ID of the photo to share
     */
    override fun sharePhotoToInstagramFeed(photoId: String) {
        coroutineScope.launch {
            if (!mediaManager.exists(photoId)) {
                throw IllegalArgumentException("Photo with ID $photoId does not exist")
            }
            try {
                context.startActivity(
                    createInstagramImageShareIntent(getUriFromMedia(photoId)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Photo with ID $photoId does not exist")
            }
        }
    }

    /**
     * Triggers the app to share a video to Instagram.
     *
     * This method will trigger the Instagram app to open and share the video. The given video ID
     * must exist. If the video does not exist, an exception will be thrown.
     *
     * @param videoId The ID of the video to share
     */
    override fun shareVideoToInstagramFeed(videoId: String) {
        coroutineScope.launch {
            if (!mediaManager.exists(videoId)) {
                throw IllegalArgumentException("Video with ID $videoId does not exist")
            }
            try {
                context.startActivity(
                    createInstagramVideoShareIntent(getUriFromMedia(videoId)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Video with ID $videoId does not exist")
            }
        }
    }

    override fun getUriFromMedia(uid: String): Uri {
        if (uid.startsWith("content://") || uid.startsWith("file://")) {
            return Uri.parse(uid)
        }

        val directFile = File(uid)
        if (directFile.exists()) {
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", directFile)
        }

        val managedMediaFile = context.filesDir.resolve("media/$uid")
        if (managedMediaFile.exists()) {
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", managedMediaFile)
        }

        return Uri.parse(uid)
    }
}
