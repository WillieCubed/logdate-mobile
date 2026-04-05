package app.logdate.client.awareness.daylight

import logdate.client.awareness.generated.resources.Res
import logdate.client.awareness.generated.resources.daylight_period_afternoon
import logdate.client.awareness.generated.resources.daylight_period_dawn
import logdate.client.awareness.generated.resources.daylight_period_evening
import logdate.client.awareness.generated.resources.daylight_period_golden_hour
import logdate.client.awareness.generated.resources.daylight_period_midday
import logdate.client.awareness.generated.resources.daylight_period_morning
import logdate.client.awareness.generated.resources.daylight_period_night
import org.jetbrains.compose.resources.StringResource

val DaylightPeriod.stringRes: StringResource
    get() =
        when (this) {
            DaylightPeriod.DAWN -> Res.string.daylight_period_dawn
            DaylightPeriod.MORNING -> Res.string.daylight_period_morning
            DaylightPeriod.MIDDAY -> Res.string.daylight_period_midday
            DaylightPeriod.AFTERNOON -> Res.string.daylight_period_afternoon
            DaylightPeriod.GOLDEN_HOUR -> Res.string.daylight_period_golden_hour
            DaylightPeriod.EVENING -> Res.string.daylight_period_evening
            DaylightPeriod.NIGHT -> Res.string.daylight_period_night
        }
