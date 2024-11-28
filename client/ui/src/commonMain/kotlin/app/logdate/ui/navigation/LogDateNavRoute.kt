package app.logdate.ui.navigation

/**
 * A sealed class representing the different routes that can be navigated to in the LogDate app.
 *
 * Each route defines its own parameters that can be passed to it. To manually differentiate between
 * routes, implementers can use the `is` operator to check the type of the route. For example:
 *
 * ```kotlin
 * when (route) {
 *    is HomeRoute -> { /* Handle the home route */ }
 *    else -> { /* Handle other routes */ }
 * }
 * ```
 */
sealed interface LogDateNavRoute

// TODO: Centralize nav routes