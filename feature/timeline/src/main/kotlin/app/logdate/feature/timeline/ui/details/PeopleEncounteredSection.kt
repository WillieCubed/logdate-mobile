package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.profiles.PersonIcon
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing

/**
 * A list of all people the user encountered on a given day.
 */
@Composable
internal fun PeopleEncounteredSection(
    people: List<PersonUiState>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("People Encountered", style = MaterialTheme.typography.titleSmall)
        if (people.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(people) { person ->
                    if (person.photoUri != null) {
                        // TODO: Fix this !!
                        PersonIcon(person.photoUri!!, person.name)
                    } else {
                        PersonIcon(person.name)
                    }
                }
            }
        } else {
            Text("No people encountered", style = MaterialTheme.typography.labelSmall)
            // TODO: Add CTA to add people
        }
    }
}