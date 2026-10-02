package app.logdate.client.device

/**
 * Desktop implementation of [AppInfoProvider].
 *
 * Desktop packages have a version string but no build number, and [AppInfo] needs both, so the
 * version is reported as unavailable instead of filled in with values that were never read.
 */
class DesktopAppInfoProvider : AppInfoProvider {
    override fun getAppInfo(): AppInfo = throw AppInfoRetrievalException("Desktop builds do not report a version and build number")
}
