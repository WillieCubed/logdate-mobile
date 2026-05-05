package app.logdate.navigation

/**
 * `true` on platforms where the Wear OS companion app integration is wired up. Android-only
 * for now; iOS and desktop expose `false` so the Watch tile in Settings stays hidden and the
 * Watch entries are never registered on the navigation graph (the underlying
 * `WatchSettingsViewModel` Koin binding lives in an Android-only DI module).
 */
expect val isWatchSupported: Boolean
