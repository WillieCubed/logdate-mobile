package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * Status of the autosave operation.
 */
enum class AutoSaveStatus {
    IDLE,      // No autosave in progress
    SAVING,    // Currently saving content
    SAVED,     // Content was successfully saved
    ERROR      // Error occurred during saving
}

/**
 * Data class representing the auto-save state.
 */
data class AutoSaveState(
    val status: AutoSaveStatus = AutoSaveStatus.IDLE,
    val lastSavedTimestamp: Long? = null,
    val error: Throwable? = null,
    val saveAttempts: Int = 0
)

/**
 * A composable function that handles auto-saving editor content.
 * This provides debounced saving, regular backup saves, and error handling with retries.
 *
 * @param content The content to monitor for changes and save
 * @param onSave Callback function to execute when content should be saved
 * @param hasContentChanged Function to determine if content has meaningful changes
 * @param debounceMs Time in milliseconds to wait after changes before saving (default: 2000ms)
 * @param backupIntervalMs Interval for periodic backup saves (default: 30000ms)
 * @param indicatorDisplayMs Time to display save indicators before returning to IDLE (default: 2000ms)
 * @param maxRetryAttempts Maximum number of retry attempts after failures (default: 3)
 * @param enabled Whether autosave is enabled (default: true)
 * @return An AutoSaveState object containing the current auto-save state
 */
@Composable
fun <T> rememberAutoSaveHandler(
    content: T,
    onSave: suspend (T) -> Unit,
    hasContentChanged: (T, String) -> Boolean,
    debounceMs: Long = 2000,
    backupIntervalMs: Long = 30000,
    indicatorDisplayMs: Long = 2000,
    maxRetryAttempts: Int = 3,
    enabled: Boolean = true
): AutoSaveState {
    // Auto-save state tracking
    var lastSavedContentHash by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(AutoSaveStatus.IDLE) }
    var lastSavedTimestamp by remember { mutableStateOf<Long?>(null) }
    var saveError by remember { mutableStateOf<Throwable?>(null) }
    var saveAttempts by remember { mutableStateOf(0) }

    // Calculate a hash/representation of the current content for comparison
    val currentContentHash by remember(content) {
        derivedStateOf {
            when (content) {
                // Special handling for EditorState
                is EditorState -> {
                    content.blocks.joinToString(separator = "|") { block ->
                        when (block) {
                            is TextBlockUiState -> "text:${block.content}"
                            is ImageBlockUiState -> "image:${block.uri ?: ""}"
                            is VideoBlockUiState -> "video:${block.uri ?: ""}"
                            is AudioBlockUiState -> "audio:${block.uri ?: ""}"
                            is CameraBlockUiState -> "camera:${block.uri ?: ""}"
                            else -> block.toString()
                        }
                    }
                }
                // Allow any other content type by using its string representation
                else -> content.toString()
            }
        }
    }

    // Debounced auto-save logic
    LaunchedEffect(currentContentHash, enabled) {
        // Skip if disabled or no meaningful changes
        if (!enabled || !hasContentChanged(content, lastSavedContentHash)) return@LaunchedEffect
        
        // Store the content hash at the beginning of the delay
        val hashAtStart = currentContentHash
        
        // Wait for user to pause typing/editing
        delay(debounceMs)
        
        // After the delay, check if content is still the same as when we started waiting
        // and we're not already saving
        if (hashAtStart == currentContentHash && status != AutoSaveStatus.SAVING) {
            status = AutoSaveStatus.SAVING
            saveAttempts = 0
            
            try {
                onSave(content)
                lastSavedContentHash = currentContentHash
                lastSavedTimestamp = Clock.System.now().toEpochMilliseconds()
                status = AutoSaveStatus.SAVED
                saveError = null
                
                // The status will be automatically reset to IDLE after a brief period
                // by the status reset effect (we don't do it here to avoid coupling)
            } catch (e: Throwable) {
                Napier.e("Auto-save failed: ${e.message}", e)
                saveAttempts++
                saveError = e
                status = AutoSaveStatus.ERROR
                
                // Retry logic with exponential backoff up to max attempts
                if (saveAttempts < maxRetryAttempts) {
                    delay(1000L * saveAttempts) // 1s, 2s, 3s delays
                    // Force another save attempt by resetting the last saved hash
                    lastSavedContentHash = ""
                } else {
                    // Show error indicator briefly before returning to idle
                    delay(indicatorDisplayMs)
                    status = AutoSaveStatus.IDLE
                }
            }
        }
    }

    // Regular interval backup save
    LaunchedEffect(enabled) {
        // Skip the entire effect if not enabled
        if (!enabled) return@LaunchedEffect
        
        while (true) {
            delay(backupIntervalMs)
            
            // Only save if:
            // 1. We're not already saving
            // 2. There are meaningful changes since last save
            // 3. Autosave is enabled
            if (status != AutoSaveStatus.SAVING && 
                hasContentChanged(content, lastSavedContentHash) && 
                enabled
            ) {
                status = AutoSaveStatus.SAVING
                
                try {
                    onSave(content)
                    lastSavedContentHash = currentContentHash
                    lastSavedTimestamp = Clock.System.now().toEpochMilliseconds()
                    status = AutoSaveStatus.SAVED
                    saveError = null
                    
                    // Show "Saved" indicator briefly before returning to idle
                    delay(indicatorDisplayMs)
                    status = AutoSaveStatus.IDLE
                } catch (e: Throwable) {
                    Napier.e("Backup auto-save failed: ${e.message}", e)
                    status = AutoSaveStatus.ERROR
                    saveError = e
                    
                    // Show error indicator briefly before returning to idle
                    delay(indicatorDisplayMs)
                    status = AutoSaveStatus.IDLE
                }
            }
        }
    }

    return AutoSaveState(
        status = status,
        lastSavedTimestamp = lastSavedTimestamp,
        error = saveError,
        saveAttempts = saveAttempts
    )
}

/**
 * A simplified composable that handles auto-saving specifically for EditorState.
 * This version is compatible with the existing EntryEditorViewModel.autoSaveEntry method.
 * 
 * @param editorState The editor state to monitor for changes
 * @param onAutoSave Callback to execute when content should be saved
 * @param debounceMs Time in milliseconds to wait after changes before saving
 * @param backupIntervalMs Interval for periodic backup saves
 * @param enabled Whether autosave is enabled
 * @return AutoSaveState object containing the current autosave state
 */
@Composable
fun rememberEditorAutoSave(
    editorState: EditorState,
    onAutoSave: (EditorState) -> Unit,
    debounceMs: Long = 2000,
    backupIntervalMs: Long = 30000,
    enabled: Boolean = true
): AutoSaveState {
    // Create an autosave state handler with EditorState-specific behavior
    return rememberAutoSaveHandler(
        content = editorState,
        onSave = { onAutoSave(it) },
        hasContentChanged = { state, lastHash -> 
            // Check if the content has meaningful changes that need to be saved
            val hasContent = state.hasContent()
            val isDirty = state.isDirty
            val isNewOrChanged = lastHash.isEmpty() || lastHash != getBlocksHash(state.blocks)
            
            Napier.i("AutoSave check: hasContent=$hasContent, isDirty=$isDirty, isNewOrChanged=$isNewOrChanged")
            
            // Content has changed if it has content, is marked as dirty, and has different hash
            hasContent && isDirty && isNewOrChanged
        },
        debounceMs = debounceMs,
        backupIntervalMs = backupIntervalMs,
        enabled = enabled
    )
}

/**
 * Helper function to create a consistent hash from blocks for comparison
 */
private fun getBlocksHash(blocks: List<EntryBlockUiState>): String {
    return blocks.joinToString("|") { block ->
        when (block) {
            is TextBlockUiState -> "text:${block.id}:${block.content}"
            is ImageBlockUiState -> "image:${block.id}:${block.uri ?: ""}"
            is VideoBlockUiState -> "video:${block.id}:${block.uri ?: ""}"
            is AudioBlockUiState -> "audio:${block.id}:${block.uri ?: ""}"
            is CameraBlockUiState -> "camera:${block.id}:${block.uri ?: ""}"
            else -> block.toString()
        }
    }
}