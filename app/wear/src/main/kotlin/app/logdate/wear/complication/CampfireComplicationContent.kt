package app.logdate.wear.complication

import androidx.annotation.DrawableRes
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.wear.R

/**
 * What the streak complication shows for a campfire: an icon for the fire's phase and the day
 * count of the current fire, or no count when there is no fire.
 */
internal data class CampfireComplicationContent(
    @param:DrawableRes val iconRes: Int,
    val runDays: Int?,
    val phase: FirePhase,
)

internal fun CampfireState.toComplicationContent(): CampfireComplicationContent =
    CampfireComplicationContent(
        iconRes =
            when (phase) {
                FirePhase.BURNING -> R.drawable.ic_campfire_flame
                FirePhase.EMBERS -> R.drawable.ic_campfire_embers
                FirePhase.UNLIT, FirePhase.OUT -> R.drawable.ic_campfire_logs
            },
        runDays = runDays.takeIf { phase == FirePhase.BURNING || phase == FirePhase.EMBERS },
        phase = phase,
    )
