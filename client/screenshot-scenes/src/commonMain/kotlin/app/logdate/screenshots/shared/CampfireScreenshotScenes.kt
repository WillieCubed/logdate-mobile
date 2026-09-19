@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.streak.Campfire
import app.logdate.ui.streak.CampfireChip
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize
import app.logdate.ui.streak.LocalCampfireAnimationEnabled
import app.logdate.ui.streak.campfireHeadline
import app.logdate.ui.theme.Spacing

/** One of each fire a user can see, from unlit through every size to embers and out. */
internal val campfireSamples: List<CampfirePresentation> =
    listOf(
        CampfirePresentation(phase = CampfirePhase.UNLIT),
        CampfirePresentation(
            phase = CampfirePhase.BURNING,
            loggedToday = true,
            runDays = 1,
            size = CampfireSize.SPARK,
            longestRunDays = 1,
            totalDaysKept = 1,
        ),
        CampfirePresentation(
            phase = CampfirePhase.BURNING,
            loggedToday = true,
            runDays = 4,
            size = CampfireSize.SMALL,
            longestRunDays = 9,
            totalDaysKept = 30,
            isRekindled = true,
        ),
        CampfirePresentation(
            phase = CampfirePhase.BURNING,
            loggedToday = true,
            runDays = 12,
            size = CampfireSize.CAMPFIRE,
            longestRunDays = 40,
            totalDaysKept = 210,
        ),
        CampfirePresentation(
            phase = CampfirePhase.BURNING,
            loggedToday = true,
            runDays = 45,
            size = CampfireSize.BONFIRE,
            longestRunDays = 45,
            totalDaysKept = 260,
        ),
        CampfirePresentation(
            phase = CampfirePhase.BURNING,
            loggedToday = true,
            runDays = 120,
            size = CampfireSize.BEACON,
            longestRunDays = 120,
            totalDaysKept = 400,
        ),
        CampfirePresentation(
            phase = CampfirePhase.BURNING,
            loggedToday = false,
            runDays = 12,
            size = CampfireSize.CAMPFIRE,
            longestRunDays = 40,
            totalDaysKept = 210,
        ),
        CampfirePresentation(
            phase = CampfirePhase.EMBERS,
            runDays = 12,
            size = CampfireSize.CAMPFIRE,
            longestRunDays = 40,
            totalDaysKept = 210,
        ),
        CampfirePresentation(
            phase = CampfirePhase.OUT,
            longestRunDays = 23,
            totalDaysKept = 64,
        ),
    )

/**
 * Renders every campfire state side by side, with the app-bar chip for each underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CampfireStatesGallery() {
    CompositionLocalProvider(LocalCampfireAnimationEnabled provides false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    campfireSamples.forEach { sample ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Campfire(
                                phase = sample.phase,
                                size = sample.size,
                                waitingForToday = sample.isWaitingForToday,
                                modifier = Modifier.size(96.dp),
                            )
                            Text(
                                text = campfireHeadline(sample),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    campfireSamples.forEach { sample -> CampfireChip(presentation = sample, onClick = {}) }
                }
            }
        }
    }
}
