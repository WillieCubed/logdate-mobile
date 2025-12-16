package app.logdate.client

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.logdate.navigation.EditorManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Extension functions for MainActivity to support multi-window operations.
 */

/**
 * Sets up support for multi-window editor functionality.
 * This should be called from the MainActivity's onCreate method.
 */
fun MainActivity.setupMultiWindowSupport() {
    val editorManager: EditorManager by inject()
    
    // Monitor window layout changes if needed
    if (editorManager.supportsMultiWindow()) {
        // Use lifecycleScope to collect flow values when the lifecycle is active
        lifecycleScope.launch {
            // Collect flow values in a coroutine
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                editorManager.getWindowLayoutInfo(this@setupMultiWindowSupport)
                    .collect { windowLayoutInfo ->
                        // Update UI based on window layout if needed
                        Napier.d("Window layout changed: $windowLayoutInfo")
                    }
            }
        }
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
        menu.add(Menu.NONE, MENU_ID_NEW_EDITOR, Menu.NONE, "New Editor Window")
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
fun MainActivity.handleMultiWindowIntent(intent: Intent) {
    val editorManager: EditorManager by inject()
    
    // Check for actions that should launch a new editor
    when (intent.action) {
        Intent.ACTION_SEND -> {
            // Extract shared content
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            
            // Open in a new window if we have content and multi-window is supported
            if (!sharedText.isNullOrBlank() && editorManager.supportsMultiWindow()) {
                editorManager.openNewEditorWindow(initialText = sharedText)
            }
        }
    }
}

// Menu item IDs
private const val MENU_ID_NEW_EDITOR = 1001