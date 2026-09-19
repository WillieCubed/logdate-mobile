@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MasterFeatureToggle
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.disabledAlpha
import app.logdate.ui.streak.Campfire
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.campfireHeadline
import app.logdate.ui.streak.campfireSupportingLine
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.streaks
import logdate.client.feature.core.generated.resources.track_journaling_streak
import logdate.client.ui.generated.resources.campfire_rekindled
import logdate.client.ui.generated.resources.campfire_rules
import logdate.client.ui.generated.resources.campfire_stat_days_kept
import logdate.client.ui.generated.resources.campfire_stat_longest_fire
import logdate.client.ui.generated.resources.campfire_stat_this_fire
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * The streak screen once the campfire replaces the consecutive-day counter: the fire itself,
 * what it means today, the three numbers that never punish a gap, the rule in one sentence, and
 * the tracking toggle.
 *
 * @param campfire The fire to show, or `null` when tracking is off or it could not be read.
 */
@Composable
fun CampfireStreakContent(
    campfire: CampfirePresentation?,
    isTrackingEnabled: Boolean,
    onBack: () -> Unit,
    onToggleStreakTracking: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = campfire ?: CampfirePresentation(phase = CampfirePhase.UNLIT)
    val trackingToggle: @Composable () -> Unit = {
        MasterFeatureToggle(
            label = stringResource(Res.string.track_journaling_streak),
            checked = isTrackingEnabled,
            onCheckedChange = onToggleStreakTracking,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }

    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                CampfireHero(campfire = shown, modifier = Modifier.disabledAlpha(isTrackingEnabled))
                if (shown.phase != CampfirePhase.UNLIT) {
                    CampfireStats(campfire = shown, modifier = Modifier.disabledAlpha(isTrackingEnabled))
                }
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                CampfireRules(modifier = Modifier.disabledAlpha(isTrackingEnabled))
                trackingToggle()
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.streaks),
                onBack = onBack,
                modifier = modifier,
            ) {
                item { CampfireHero(campfire = shown, modifier = Modifier.disabledAlpha(isTrackingEnabled)) }
                if (shown.phase != CampfirePhase.UNLIT) {
                    item { CampfireStats(campfire = shown, modifier = Modifier.disabledAlpha(isTrackingEnabled)) }
                }
                item { CampfireRules(modifier = Modifier.disabledAlpha(isTrackingEnabled)) }
                item { trackingToggle() }
            }
        },
    )
}

@Composable
private fun CampfireHero(
    campfire: CampfirePresentation,
    modifier: Modifier = Modifier,
) {
    val headline = campfireHeadline(campfire)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Campfire(
            phase = campfire.phase,
            size = campfire.size,
            waitingForToday = campfire.isWaitingForToday,
            contentDescription = headline,
            modifier = Modifier.size(168.dp),
        )
        if (campfire.showsRekindledLabel) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(UiRes.string.campfire_rekindled),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = campfireSupportingLine(campfire),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CampfireStats(
    campfire: CampfirePresentation,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // A fire that went out has no current length; a dash reads as "no fire" rather than a score of zero.
            CampfireStat(
                value = campfire.runDays.takeIf { it > 0 },
                label = stringResource(UiRes.string.campfire_stat_this_fire),
            )
            CampfireStat(value = campfire.longestRunDays, label = stringResource(UiRes.string.campfire_stat_longest_fire))
            CampfireStat(value = campfire.totalDaysKept, label = stringResource(UiRes.string.campfire_stat_days_kept))
        }
    }
}

@Composable
private fun CampfireStat(
    value: Int?,
    label: String,
) {
    val valueText = value?.toString() ?: NO_VALUE
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = if (value == null) label else "$label: $value"
            },
    ) {
        Text(
            text = valueText,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun CampfireRules(modifier: Modifier = Modifier) {
    MaterialContainer(modifier = modifier.padding(horizontal = Spacing.lg)) {
        Text(
            text = stringResource(UiRes.string.campfire_rules),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
        )
    }
}

private const val NO_VALUE = "–"
