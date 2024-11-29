package app.logdate.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
internal fun PeopleMetadataChip(
    people: List<String>,
) {
    // Label logic:
    // If there is only one other person, display that name.
    // If there are two people, display both names separated by "and".
    // If there are more than two people, display the first two names followed by a count of the remaining people.
    // e.g. If there are four people (Lane, Anna, Willie, and Jake), the text should be "Lane, Anna, and 2 others".
    val label = when (people.size) {
        0 -> "Just you"
        1 -> people.first()
        2 -> "${people.first()} and ${people.last()}"
        else -> "${people.take(2).joinToString()} and ${people.size - 2} others"
    }
    MetadataChip(
        label = label,
        icon = {
            Icon(
                Icons.Rounded.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}