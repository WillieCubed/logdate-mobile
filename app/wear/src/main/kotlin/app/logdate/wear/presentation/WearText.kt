package app.logdate.wear.presentation

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Text a watch screen shows, carried in UI state.
 *
 * The watch keeps its own words in Android string resources. A ViewModel names the resource and its
 * arguments, and [asString] looks up the words for the watch's language during composition. [Raw]
 * holds text that is already final, such as a message the phone sent in the phone's language.
 */
sealed interface WearText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : WearText

    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : WearText

    data class Raw(
        val value: String,
    ) : WearText
}

@Composable
fun WearText.asString(): String =
    when (this) {
        is WearText.Resource -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
        is WearText.Plural -> pluralStringResource(id, quantity, *args.toTypedArray())
        is WearText.Raw -> value
    }
