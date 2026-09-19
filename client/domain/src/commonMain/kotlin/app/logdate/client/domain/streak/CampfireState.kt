package app.logdate.client.domain.streak

/**
 * Where the user's journaling fire stands today.
 */
enum class FirePhase {
    /** Nothing has ever been logged. */
    UNLIT,

    /** Something was logged today, or yesterday and today is still open. */
    BURNING,

    /** Yesterday was missed. Logging today rekindles the fire at its full size. */
    EMBERS,

    /** Two days in a row were missed, so the fire went out. */
    OUT,
}

/**
 * How big the current fire has grown, from the number of days logged on it.
 */
enum class FireSize {
    SPARK,
    SMALL,
    CAMPFIRE,
    BONFIRE,
    BEACON,
    ;

    companion object {
        /**
         * The size of a fire with [runDays] logged days, or `null` when there is no fire.
         */
        fun forRunDays(runDays: Int): FireSize? =
            when {
                runDays <= 0 -> null
                runDays < 3 -> SPARK
                runDays < 7 -> SMALL
                runDays < 30 -> CAMPFIRE
                runDays < 100 -> BONFIRE
                else -> BEACON
            }
    }
}

/**
 * A forgiving view of the user's journaling streak.
 *
 * Each logged day adds to the current fire. One missed day lets it burn down to [FirePhase.EMBERS]
 * without ending it; two missed days in a row put it out. The longest fire and the lifetime total
 * never go down, so a fire going out never erases what came before.
 *
 * @property phase Where the fire stands today.
 * @property loggedToday Whether anything has been logged in the current streak day.
 * @property runDays Days logged on the current fire; 0 when [phase] is [FirePhase.UNLIT] or [FirePhase.OUT].
 * @property size How big the current fire is; `null` when there is no fire.
 * @property longestRunDays Days logged on the longest fire, including the current one.
 * @property totalDaysJournaled Distinct days with at least one entry, across all history.
 * @property isRekindled Whether the current fire began after an earlier fire went out.
 */
data class CampfireState(
    val phase: FirePhase,
    val loggedToday: Boolean,
    val runDays: Int,
    val size: FireSize?,
    val longestRunDays: Int,
    val totalDaysJournaled: Int,
    val isRekindled: Boolean,
)
