package app.logdate.feature.editor.ui.text

import androidx.compose.runtime.saveable.Saver

/** Saveable inputs reset a live composition, but restored values also need explicit ownership validation. */
internal fun <T : Any> ownedTextInputSaver(
    owner: String,
    delegate: Saver<T, Any>,
    initial: T,
): Saver<T, Any> =
    Saver(
        save = { value -> listOf(owner, with(delegate) { save(value) }) },
        restore = { saved ->
            val values = saved as List<*>
            if (values[0] == owner) values[1]?.let { delegate.restore(it) } ?: initial else initial
        },
    )
