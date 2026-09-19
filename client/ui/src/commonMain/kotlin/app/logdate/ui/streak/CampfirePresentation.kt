package app.logdate.ui.streak

/**
 * Where the journaling fire stands today, as the UI draws it.
 */
enum class CampfirePhase {
    UNLIT,
    BURNING,
    EMBERS,
    OUT,
}

/**
 * How big the fire is drawn, from a small spark up to a beacon.
 */
enum class CampfireSize {
    SPARK,
    SMALL,
    CAMPFIRE,
    BONFIRE,
    BEACON,
}

/**
 * UI-only projection of the user's journaling streak. Composables in this package render against
 * [CampfirePresentation] directly and never see domain types, so the same campfire works in any
 * feature, in previews, and in screenshot tests.
 *
 * The mapping from the domain's campfire state lives in a view model outside this module, since
 * `client/ui` deliberately doesn't depend on `client/domain`.
 *
 * @property phase Where the fire stands today.
 * @property loggedToday Whether anything has been logged in the current streak day.
 * @property runDays Days logged on the current fire; 0 when unlit or out.
 * @property size How big the current fire is drawn; `null` when there is no fire.
 * @property longestRunDays Days logged on the longest fire, including the current one.
 * @property totalDaysKept Distinct days with at least one entry, across all history.
 * @property isRekindled Whether the current fire began after an earlier fire went out.
 */
data class CampfirePresentation(
    val phase: CampfirePhase,
    val loggedToday: Boolean = false,
    val runDays: Int = 0,
    val size: CampfireSize? = null,
    val longestRunDays: Int = 0,
    val totalDaysKept: Int = 0,
    val isRekindled: Boolean = false,
) {
    /**
     * Whether the fire is still lit but nothing has been added in the current streak day.
     */
    val isWaitingForToday: Boolean
        get() = phase == CampfirePhase.BURNING && !loggedToday

    /**
     * Whether to label the fire as rekindled. The label marks a comeback, so it fades once the new
     * fire has grown into a campfire of its own.
     */
    val showsRekindledLabel: Boolean
        get() = isRekindled && runDays < REKINDLED_LABEL_MAX_RUN_DAYS

    private companion object {
        const val REKINDLED_LABEL_MAX_RUN_DAYS = 7
    }
}
