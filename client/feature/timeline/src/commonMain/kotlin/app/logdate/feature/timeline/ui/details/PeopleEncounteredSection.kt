package app.logdate.feature.timeline.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import org.jetbrains.compose.resources.stringResource
import logdate.client.feature.timeline.generated.resources.*
import logdate.client.feature.timeline.generated.resources.Res
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
        Text(stringResource(Res.string.people_encountered), style = MaterialTheme.typography.titleSmall)
        val showPeople = people.isNotEmpty()
        AnimatedVisibility(visible = showPeople) {
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
        }
        AnimatedVisibility(
            visible = !showPeople,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(stringResource(Res.string.no_people_encountered), style = MaterialTheme.typography.labelSmall)
            // TODO: Add CTA to add people
        }
    }
}