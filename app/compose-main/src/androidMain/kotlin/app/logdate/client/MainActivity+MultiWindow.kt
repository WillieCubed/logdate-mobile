@file:Suppress("ktlint:standard:filename")

package app.logdate.client

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import app.logdate.client.media.MediaManager
import app.logdate.client.shortcuts.parseLauncherShortcutTargetJournalId
import app.logdate.navigation.EditorManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Initializes the multi-window feature gate without starting any background observers.
 */
fun MainActivity.setupMultiWindowSupport() {
    val editorManager: EditorManager by inject()
    if (!editorManager.supportsMultiWindow()) {
        return
    }
}

/**
 * Creates menu options for multi-window support.
 * This should be called from the MainActivity's onCreateOptionsMenu method.
 *
 * @param menu The menu to add items to
 * @return true if the menu was modified
 */
fun MainActivity.createMultiWindowMenuOptions(menu: Menu): Boolean {
    val editorManager: EditorManager by inject()

    if (editorManager.supportsMultiWindow()) {
        menu
            .add(Menu.NONE, MENU_ID_NEW_EDITOR, Menu.NONE, "New Editor Window")
            .setIcon(android.R.drawable.ic_menu_add)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    return true
}

/**
 * Handles menu selections for multi-window support.
 * This should be called from the MainActivity's onOptionsItemSelected method.
 *
 * @param item The selected menu item
 * @return true if the item was handled
 */
fun MainActivity.handleMultiWindowMenuSelection(item: MenuItem): Boolean {
    val editorManager: EditorManager by inject()

    return when (item.itemId) {
        MENU_ID_NEW_EDITOR -> {
            editorManager.openNewEditorWindow()
            true
        }
        else -> false
    }
}

/**
 * Handles incoming intents for the multi-window functionality.
 * This should be called from the MainActivity's onNewIntent method.
 *
 * @param intent The new intent received
 */
fun MainActivity.handleMultiWindowIntent(intent: Intent): Boolean {
    val editorManager: EditorManager by inject()
    val mediaManager: MediaManager by inject()

    // Check for actions that should launch a new editor
    return when (intent.action) {
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        -> {
            lifecycleScope.launch {
                val sharedContent = importIncomingEditorShare(intent, mediaManager)
                if (sharedContent == null) {
                    Napier.w("Ignored unsupported incoming share intent")
                    return@launch
                }
                editorManager.openNewEditorWindow(
                    initialText = sharedContent.initialText,
                    attachments = sharedContent.attachments,
                    journalIds = sharedContent.targetJournalIds,
                )
                finish()
            }
            true
        }

        Intent.ACTION_VIEW -> {
            val targetJournalId = intent.parseLauncherShortcutTargetJournalId() ?: return false
            editorManager.openNewEditorWindow(journalIds = listOf(targetJournalId))
            finish()
            true
        }

        else -> {
            false
        }
    }
}

// Menu item IDs
private const val MENU_ID_NEW_EDITOR = 1001
