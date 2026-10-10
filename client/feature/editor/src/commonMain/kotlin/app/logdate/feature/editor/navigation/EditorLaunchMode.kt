package app.logdate.feature.editor.navigation

import kotlinx.serialization.Serializable

/**
 * What a brand-new entry opens ready to capture.
 *
 * Every surface that starts an entry (launcher shortcuts, widgets, Quick Settings tiles,
 * notifications, assistants, keyboard shortcuts) says which mode it wants instead of inventing its
 * own flags, so the editor has one place that prepares the first block.
 */
@Serializable
enum class EditorLaunchMode {
    /** An empty text entry with the keyboard up. */
    Text,

    /** A voice recording that starts right away. */
    Voice,

    /** The camera, ready to take a photo. */
    Camera,

    /** The camera, ready to record video. */
    Video,
}
