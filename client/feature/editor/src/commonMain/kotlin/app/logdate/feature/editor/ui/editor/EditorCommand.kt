package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Represents a command that can be executed and undone.
 * This is the base interface for all editor operations that support undo/redo.
 */
sealed interface EditorCommand {
    val timestamp: Long
    fun execute()
    fun undo()
}

/**
 * Commands specific to text block modifications
 */
sealed class TextCommand : EditorCommand {
    data class UpdateContent(
        val blockId: String,
        val oldContent: String,
        val newContent: String,
        val updateBlock: (String, String) -> Unit,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : TextCommand() {
        override fun execute() {
            updateBlock(blockId, newContent)
        }
        
        override fun undo() {
            updateBlock(blockId, oldContent)
        }
    }
}

/**
 * Commands for block management (adding, removing, reordering)
 */
sealed class BlockCommand : EditorCommand {
    data class AddBlock(
        val block: EntryBlockData,
        val addBlock: (EntryBlockData) -> Unit,
        val removeBlock: (Uuid) -> Unit,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : BlockCommand() {
        override fun execute() {
            addBlock(block)
        }
        
        override fun undo() {
            removeBlock(block.id)
        }
    }
    
    data class RemoveBlock(
        val block: EntryBlockData,
        val index: Int,
        val addBlockAt: (EntryBlockData, Int) -> Unit,
        val removeBlock: (Uuid) -> Unit,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : BlockCommand() {
        override fun execute() {
            removeBlock(block.id)
        }
        
        override fun undo() {
            addBlockAt(block, index)
        }
    }
    
    data class MoveBlock(
        val blockId: String,
        val oldIndex: Int,
        val newIndex: Int,
        val moveBlock: (String, Int) -> Unit,
        override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : BlockCommand() {
        override fun execute() {
            moveBlock(blockId, newIndex)
        }
        
        override fun undo() {
            moveBlock(blockId, oldIndex)
        }
    }
}

/**
 * Manages the shared command history for the entire editor.
 * This class handles the undo/redo stack and command grouping logic.
 */
class SharedHistoryManager {
    // Stacks for undo and redo operations
    private val undoStack = mutableStateListOf<EditorCommand>()
    private val redoStack = mutableStateListOf<EditorCommand>()
    
    // Observable state for UI updates
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    
    /**
     * Executes a new command and adds it to the history.
     * Also handles command merging for rapid text updates.
     */
    fun executeCommand(command: EditorCommand) {
        // Try to merge text commands if they're close together in time
        if (command is TextCommand.UpdateContent && undoStack.lastOrNull() is TextCommand.UpdateContent) {
            val lastCommand = undoStack.last() as TextCommand.UpdateContent
            if (shouldMergeTextCommands(lastCommand, command)) {
                // Replace the last command with a merged one
                undoStack[undoStack.lastIndex] = TextCommand.UpdateContent(
                    blockId = command.blockId,
                    oldContent = lastCommand.oldContent,
                    newContent = command.newContent,
                    updateBlock = command.updateBlock,
                    timestamp = command.timestamp
                )
                command.execute()
                updateState()
                return
            }
        }
        
        // Clear redo stack when new command is executed
        redoStack.clear()
        
        // Execute and add to undo stack
        command.execute()
        undoStack.add(command)
        
        // Maintain maximum history size
        if (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeFirst()
        }
        
        updateState()
    }
    
    fun undo() {
        undoStack.removeLastOrNull()?.let { command ->
            command.undo()
            redoStack.add(command)
            updateState()
        }
    }
    
    fun redo() {
        redoStack.removeLastOrNull()?.let { command ->
            command.execute()
            undoStack.add(command)
            updateState()
        }
    }
    
    /**
     * Determines if two text commands should be merged based on timing and content
     */
    private fun shouldMergeTextCommands(
        previous: TextCommand.UpdateContent,
        current: TextCommand.UpdateContent
    ): Boolean {
        // Only merge commands for the same block
        if (previous.blockId != current.blockId) return false
        
        // Only merge commands that are close together in time
        val timeDiff = current.timestamp - previous.timestamp
        if (timeDiff > MERGE_THRESHOLD_MS) return false
        
        // Only merge if the content changes are sequential
        return previous.newContent == current.oldContent
    }
    
    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
    
    companion object {
        private const val MAX_HISTORY_SIZE = 100
        private const val MERGE_THRESHOLD_MS = 1000 // 1 second
    }
}