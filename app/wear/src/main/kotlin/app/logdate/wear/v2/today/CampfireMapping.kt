package app.logdate.wear.v2.today

import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.FireSize
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.streak.CampfireSize

internal fun CampfireState.toPresentation(): CampfirePresentation =
    CampfirePresentation(
        phase =
            when (phase) {
                FirePhase.UNLIT -> CampfirePhase.UNLIT
                FirePhase.BURNING -> CampfirePhase.BURNING
                FirePhase.EMBERS -> CampfirePhase.EMBERS
                FirePhase.OUT -> CampfirePhase.OUT
            },
        loggedToday = loggedToday,
        runDays = runDays,
        size =
            when (size) {
                null -> null
                FireSize.SPARK -> CampfireSize.SPARK
                FireSize.SMALL -> CampfireSize.SMALL
                FireSize.CAMPFIRE -> CampfireSize.CAMPFIRE
                FireSize.BONFIRE -> CampfireSize.BONFIRE
                FireSize.BEACON -> CampfireSize.BEACON
            },
        longestRunDays = longestRunDays,
        totalDaysJournaled = totalDaysJournaled,
        isRekindled = isRekindled,
    )
