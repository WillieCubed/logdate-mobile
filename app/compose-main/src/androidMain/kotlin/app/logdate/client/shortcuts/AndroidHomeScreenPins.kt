package app.logdate.client.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import app.logdate.client.MainActivity
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.ui.common.HomeScreenPins
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Pins journals to the home screen as launcher shortcuts that open the journal through its
 * `logdate://journal/{id}` deep link. The icon is the journal's cover or monogram, the same as its
 * share target.
 */
class AndroidHomeScreenPins(
    private val context: Context,
    private val icons: JournalShortcutIcons,
    private val journals: JournalRepository,
    private val scope: CoroutineScope,
) : HomeScreenPins {
    override val canPin: Boolean
        get() = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    override fun pinJournal(
        journalId: Uuid,
        title: String,
    ) {
        scope.launch {
            runCatching {
                val cover = journals.getJournalById(journalId)?.coverImageUri
                val shortcut =
                    ShortcutInfoCompat
                        .Builder(context, pinnedJournalShortcutId(journalId))
                        .setShortLabel(title)
                        .setIcon(icons.icon(journalId, title, cover))
                        .setIntent(
                            Intent(Intent.ACTION_VIEW, "logdate://journal/$journalId".toUri())
                                .setClass(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        ).build()
                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            }.onFailure { error -> Napier.e("Could not ask the launcher to pin journal $journalId", error) }
        }
    }
}

/** The id of the pinned shortcut for a journal, kept apart from its share target's id. */
fun pinnedJournalShortcutId(journalId: Uuid): String = "logdate.shortcut.journal:$journalId"
