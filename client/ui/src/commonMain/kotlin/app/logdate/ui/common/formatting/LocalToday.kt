package app.logdate.ui.common.formatting

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The date treated as "today" when rendering relative dates like "Yesterday".
 *
 * Defaults to the real date, which is what the app wants. Screenshot scenes override it so a
 * recorded image stays correct: read from the clock instead, "Yesterday" silently becomes "May 4"
 * the next day and every reference showing it is wrong from then on, with no code having changed.
 */
val LocalToday =
    staticCompositionLocalOf<LocalDate> {
        Clock.System.todayIn(TimeZone.currentSystemDefault())
    }
