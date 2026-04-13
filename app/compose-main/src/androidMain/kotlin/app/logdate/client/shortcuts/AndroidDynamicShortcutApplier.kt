package app.logdate.client.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.logdate.client.MainActivity
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_DRAFT
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_RECALL_DATE
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.feature.widgets.R
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutDescriptor
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_ID
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_TARGET
import app.logdate.client.rewind.REWIND_NOTIFICATION_TARGET_DETAIL
import app.logdate.feature.journals.ui.deriveCoverColor
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

/**
 * Converts [DynamicShortcutDescriptor]s into Android `ShortcutInfoCompat` instances and
 * publishes them through `ShortcutManagerCompat`.
 *
 * Two logical groups end up in the same atomic `setDynamicShortcuts` call:
 *
 * - **Launcher group** — `ContinueDraft`, `TodayTimeline`, `WeekRewind`. These reuse the
 *   existing AMBIENT_PROMPT and REWIND_NOTIFICATION intent extras that
 *   `MainActivity.resolveNavKey` already understands. Synchronous to build (vector
 *   drawable icons), and ranked 0..2 so they appear at the top of the launcher long-press
 *   menu.
 * - **Sharing group** — one [DynamicShortcutDescriptor.ShareToJournal] per journal,
 *   marked with the conversation category so the OS hoists them into the share sheet.
 *   Each one carries a per-journal cover bitmap (loaded via Coil from the journal's
 *   `coverImageUri`, or a deterministic colored circle from [deriveCoverColor] if no
 *   cover is set — same color the in-app `JournalCover` uses, so visual identity stays
 *   consistent). Ranked at [SHARING_RANK_BASE]+ so they overflow past the launcher
 *   entries in long-press while staying eligible for the share sheet.
 *
 * The publisher emits sharing descriptors uncapped; this applier queries the
 * system's per-activity shortcut limit at apply time and trims the sharing tail
 * if needed (most-recently-updated journals win, since the publisher already
 * sorts by `lastUpdated`).
 *
 * `apply` is `suspend` because the per-journal icon loading is async — the
 * worker that calls it is already a CoroutineWorker, so this is natural.
 */
class AndroidDynamicShortcutApplier(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {
    suspend fun apply(descriptors: List<DynamicShortcutDescriptor>) {
        val (sharingDescriptors, launcherDescriptors) =
            descriptors.partition { it is DynamicShortcutDescriptor.ShareToJournal }

        val systemLimit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val launcherSlots = launcherDescriptors.take(systemLimit)
        val remainingSlots = (systemLimit - launcherSlots.size).coerceAtLeast(0)
        val sharingSlots =
            sharingDescriptors
                .filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>()
                .take(remainingSlots)

        val launcherShortcuts =
            launcherSlots.mapIndexed { index, descriptor ->
                buildLauncherShortcut(descriptor, rank = index)
            }

        val sharingShortcuts =
            coroutineScope {
                sharingSlots
                    .mapIndexed { index, descriptor ->
                        async { buildShareToJournal(descriptor, rank = SHARING_RANK_BASE + index) }
                    }.awaitAll()
            }

        ShortcutManagerCompat.setDynamicShortcuts(context, launcherShortcuts + sharingShortcuts)
    }

    private fun buildLauncherShortcut(
        descriptor: DynamicShortcutDescriptor,
        rank: Int,
    ): ShortcutInfoCompat =
        when (descriptor) {
            is DynamicShortcutDescriptor.ContinueDraft -> buildContinueDraft(descriptor, rank)
            is DynamicShortcutDescriptor.TodayTimeline -> buildTodayTimeline(descriptor, rank)
            is DynamicShortcutDescriptor.WeekRewind -> buildWeekRewind(descriptor, rank)
            is DynamicShortcutDescriptor.ShareToJournal ->
                error("Sharing descriptors are routed through the async builder")
        }

    private fun buildContinueDraft(
        descriptor: DynamicShortcutDescriptor.ContinueDraft,
        rank: Int,
    ): ShortcutInfoCompat {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_DRAFT)
                putExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID, descriptor.draftId.toString())
            }
        return ShortcutInfoCompat
            .Builder(context, descriptor.id)
            .setShortLabel(context.getString(R.string.dynamic_shortcut_continue_draft_short))
            .setLongLabel(context.getString(R.string.dynamic_shortcut_continue_draft_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_continue_draft))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    private fun buildTodayTimeline(
        descriptor: DynamicShortcutDescriptor.TodayTimeline,
        rank: Int,
    ): ShortcutInfoCompat {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_MEMORY_RECALL)
                putExtra(EXTRA_AMBIENT_PROMPT_RECALL_DATE, descriptor.date.toString())
            }
        return ShortcutInfoCompat
            .Builder(context, descriptor.id)
            .setShortLabel(context.getString(R.string.dynamic_shortcut_today_short))
            .setLongLabel(context.getString(R.string.dynamic_shortcut_today_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_today))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    private fun buildWeekRewind(
        descriptor: DynamicShortcutDescriptor.WeekRewind,
        rank: Int,
    ): ShortcutInfoCompat {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_REWIND_NOTIFICATION_TARGET, REWIND_NOTIFICATION_TARGET_DETAIL)
                putExtra(EXTRA_REWIND_NOTIFICATION_ID, descriptor.rewindId.toString())
            }
        return ShortcutInfoCompat
            .Builder(context, descriptor.id)
            .setShortLabel(context.getString(R.string.dynamic_shortcut_week_rewind_short))
            .setLongLabel(context.getString(R.string.dynamic_shortcut_week_rewind_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_week_rewind))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    private suspend fun buildShareToJournal(
        descriptor: DynamicShortcutDescriptor.ShareToJournal,
        rank: Int,
    ): ShortcutInfoCompat {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_SHORTCUT_TARGET_JOURNAL_ID, descriptor.journalId.toString())
            }
        val icon = loadJournalIcon(descriptor)
        val person =
            Person
                .Builder()
                .setName(descriptor.journalTitle)
                .setKey(descriptor.id)
                .build()
        return ShortcutInfoCompat
            .Builder(context, descriptor.id)
            .setShortLabel(descriptor.journalTitle)
            .setLongLabel(
                context.getString(
                    R.string.dynamic_shortcut_share_to_journal_long,
                    descriptor.journalTitle,
                ),
            ).setIcon(icon)
            .setIntent(intent)
            .setRank(rank)
            .setLongLived(true)
            .setCategories(setOf(SHORTCUT_CATEGORY_CONVERSATION))
            .setPerson(person)
            .build()
    }

    private suspend fun loadJournalIcon(descriptor: DynamicShortcutDescriptor.ShareToJournal): IconCompat {
        val coverBitmap =
            descriptor.coverImageUri?.let { uri ->
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(uri)
                            .size(SHORTCUT_ICON_PX)
                            .allowHardware(false)
                            .build()
                    val result = imageLoader.execute(request)
                    (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap
                }.onFailure { error ->
                    Napier.w("Failed to load journal cover for sharing shortcut: $uri", error)
                }.getOrNull()
            }

        val bitmap = coverBitmap ?: renderColorCircleBitmap(descriptor.journalId)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    private fun renderColorCircleBitmap(journalId: Uuid): Bitmap {
        val bitmap = Bitmap.createBitmap(SHORTCUT_ICON_PX, SHORTCUT_ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = deriveCoverColor(journalId).toArgb()
            }
        // Full-bleed solid color: the launcher's adaptive-icon mask handles cropping to its
        // shape (circle on Pixel, squircle elsewhere). Drawing a circle inside the bitmap
        // would slice off the safe-zone padding.
        canvas.drawRect(0f, 0f, SHORTCUT_ICON_PX.toFloat(), SHORTCUT_ICON_PX.toFloat(), paint)
        return bitmap
    }

    private companion object {
        /**
         * Pixel size for adaptive shortcut icons. 108dp at xxxhdpi works out to ~324px and
         * is the recommended adaptive-icon canvas size.
         */
        const val SHORTCUT_ICON_PX = 324

        /**
         * Rank offset that pushes sharing shortcuts past launcher shortcuts in long-press
         * menu ordering. Sharing shortcuts still appear in the share sheet regardless of
         * rank — this only affects launcher long-press visibility.
         */
        const val SHARING_RANK_BASE = 100

        /**
         * Conversation category required for the system to surface a dynamic shortcut as a
         * Direct Share target. Mirrored from `ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION`.
         */
        const val SHORTCUT_CATEGORY_CONVERSATION = "android.shortcut.conversation"
    }
}
