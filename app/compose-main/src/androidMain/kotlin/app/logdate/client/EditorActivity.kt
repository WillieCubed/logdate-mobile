package app.logdate.client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowMetricsCalculator
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
// Remove EditorInstanceId import until we properly implement it
import app.logdate.ui.theme.LogDateTheme
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext
import kotlin.uuid.Uuid

/**
 * Activity dedicated to handling editor windows.
 * 
 * This activity is designed to be launched multiple times, with each instance
 * having its own window and editor state. It handles window lifecycle events
 * and auto-saves content when appropriate.
 */
class EditorActivity : FragmentActivity() {
    
    private val viewModel by viewModel<EntryEditorViewModel>()
    // We'll implement EditorInstanceId handling later
    private var instanceId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize FileKit for file operations
        FileKit.init(this)
        
        // Extract initial content if provided
        val initialText = intent.getStringExtra(EXTRA_INITIAL_TEXT)
        val attachmentUris = intent.getStringArrayListExtra(EXTRA_ATTACHMENTS)?.toList() ?: emptyList()
        
        // Configure window features
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        
        // Set up lifecycle-aware operations
        setupLifecycleHandling()
        
        setContent {
            KoinContext {
                LogDateTheme {
                    NoteEditorScreen(
                        onNavigateBack = { finish() },
                        onEntrySaved = { 
                            setResult(RESULT_OK)
                            finish() 
                        },
                        initialTextContent = initialText,
                        attachments = attachmentUris,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
    
    /**
     * Sets up handling of lifecycle events to ensure editor content is saved
     * when appropriate and window size changes are handled.
     */
    private fun setupLifecycleHandling() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Update window metrics whenever the window becomes active
                updateWindowMetrics()
            }
        }
        
        // Trigger auto-save when pausing the activity (going to background)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Napier.d("Editor window is visible")
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Auto-save content when going to background
        val editorState = viewModel.editorState.value
        if (editorState.isDirty) {
            Napier.d("Auto-saving editor content on pause")
            viewModel.autoSaveEntry(editorState)
        }
    }
    
    /**
     * Calculates and updates window metrics for the editor.
     */
    private fun updateWindowMetrics() {
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
        val widthDp = metrics.bounds.width() / resources.displayMetrics.density
        val heightDp = metrics.bounds.height() / resources.displayMetrics.density
        
        Napier.d("Window metrics updated: ${widthDp}dp x ${heightDp}dp")
        
        // Adapt UI based on window size if needed
        // For example, we could enable/disable certain features based on window size
        // This is handled internally in the ViewModel to keep UI concerns separate
    }
    
    companion object {
        private const val EXTRA_INSTANCE_ID = "editor_instance_id"
        private const val EXTRA_INITIAL_TEXT = "initial_text"
        private const val EXTRA_ATTACHMENTS = "attachments"
        
        /**
         * Creates an intent to launch a new editor window.
         *
         * @param context The application context
         * @param initialText Optional initial text content
         * @param attachments Optional list of attachment URIs
         * @return Intent configured to launch the editor activity
         */
        fun createIntent(
            context: Context,
            initialText: String? = null,
            attachments: List<String>? = null
        ): Intent {
            return Intent(context, EditorActivity::class.java).apply {
                initialText?.let {
                    putExtra(EXTRA_INITIAL_TEXT, it)
                }
                attachments?.let {
                    putStringArrayListExtra(EXTRA_ATTACHMENTS, ArrayList(it))
                }
                
                // Flags to launch as a new document/task
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }
    }
}