package app.logdate.client.launch

/** A browser can handle updates when Play Store is unavailable on the receiving device. */
internal fun openLogDateUpdate(
    packageName: String,
    launch: (String) -> Boolean,
): Boolean = launch("market://details?id=$packageName") || launch("https://play.google.com/store/apps/details?id=$packageName")
