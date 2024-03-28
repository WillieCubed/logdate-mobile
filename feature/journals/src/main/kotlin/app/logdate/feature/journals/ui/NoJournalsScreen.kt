package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.logdate.feature.journals.R
import app.logdate.ui.theme.Spacing

@Composable
internal fun NoJournalsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(Spacing.lg)
            .fillMaxHeight(
                fraction = 0.5f,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.state_journals_empty_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                stringResource(R.string.state_journals_empty_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.state_journals_empty_cta),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}