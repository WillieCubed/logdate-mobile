package app.logdate.client.shortcuts

/**
 * How many dynamic shortcuts LogDate may publish. Android allows [maxPerActivity] shortcuts for the
 * launcher activity in all, counting the [manifestShortcuts] declared in `shortcuts.xml` and the
 * [liveShortcuts] a running surface keeps, so publishing more throws and nothing updates.
 */
fun dynamicShortcutSlots(
    maxPerActivity: Int,
    manifestShortcuts: Int,
    liveShortcuts: Int,
): Int = (maxPerActivity - manifestShortcuts - liveShortcuts).coerceAtLeast(0)
