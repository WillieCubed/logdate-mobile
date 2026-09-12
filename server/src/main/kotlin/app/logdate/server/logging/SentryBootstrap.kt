package app.logdate.server.logging

import app.logdate.server.config.RuntimeProfile
import io.github.aakira.napier.Napier
import io.sentry.Sentry

// See docs/observability/sentry.md for the full integration story.
internal fun initializeSentry(
    profile: RuntimeProfile,
    readEnv: (String) -> String? = System::getenv,
) {
    val dsn = readEnv("SENTRY_DSN").orEmpty()
    if (dsn.isEmpty()) {
        if (profile.isProduction) {
            Napier.w("SENTRY_DSN unset in production — uncaught exceptions and route errors won't reach Sentry.")
        }
        return
    }
    // Sentry's release tag should change every deploy so the dashboard can
    // attribute regressions to a specific commit. Set RELEASE_VERSION at
    // deploy time (e.g. `logdate-server@<short-sha>` from the deploy workflow);
    // otherwise we record an explicit "unknown" so the gap shows up rather
    // than every deploy collapsing into one synthetic 1.0.0 version.
    val release = readEnv("RELEASE_VERSION")?.trim().takeUnless { it.isNullOrEmpty() } ?: "logdate-server@unknown"
    Sentry.init { options ->
        options.dsn = dsn
        options.environment = profile.name.lowercase()
        options.release = release
    }
    Napier.i("Sentry initialised for ${profile.name.lowercase()} (release=$release)")
}
