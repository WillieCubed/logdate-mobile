package app.logdate.client.shortcuts

import android.content.Context
import android.content.Intent
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

/**
 * Converts [DynamicShortcutDescriptor]s into Android `ShortcutInfoCompat` instances and
 * publishes them through `ShortcutManagerCompat`.
 *
 * Each descriptor maps to a launcher shortcut whose Intent reuses the existing
 * deep-link extras that `MainActivity.resolveNavKey` already understands — no
 * new intent-routing branches are required.
 *
 * The `setDynamicShortcuts` call atomically replaces the previous dynamic set,
 * so callers can pass the full desired list every refresh and removed entries
 * disappear from the launcher automatically.
 */
class AndroidDynamicShortcutApplier(
    private val context: Context,
) {
    fun apply(descriptors: List<DynamicShortcutDescriptor>) {
        val shortcuts =
            descriptors.mapIndexed { index, descriptor ->
                descriptor.toShortcutInfo(rank = index)
            }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun DynamicShortcutDescriptor.toShortcutInfo(rank: Int): ShortcutInfoCompat =
        when (this) {
            is DynamicShortcutDescriptor.ContinueDraft -> buildContinueDraft(this, rank)
            is DynamicShortcutDescriptor.TodayTimeline -> buildTodayTimeline(this, rank)
            is DynamicShortcutDescriptor.WeekRewind -> buildWeekRewind(this, rank)
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
}
