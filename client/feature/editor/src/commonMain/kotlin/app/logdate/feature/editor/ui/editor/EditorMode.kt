package app.logdate.feature.editor.ui.editor

/**
 * Represents the different editing modes available in the editor.
 */
enum class EditorMode {
    TEXT,
    AUDIO;
    
    companion object {
        /**
         * Returns the corresponding page index for this editor mode.
         */
        fun EditorMode.toPageIndex(): Int = this.ordinal
        
        /**
         * Returns the editor mode for the given page index.
         */
        fun fromPageIndex(index: Int): EditorMode {
            return values().getOrNull(index) ?: TEXT
        }
    }
}