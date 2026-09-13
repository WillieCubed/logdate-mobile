package app.logdate.server.openapi

import app.logdate.server.ratelimit.RateLimitPolicy
import app.logdate.server.routes.CLOUD_TRANSCRIPTION_SESSION_LIMIT
import app.logdate.server.routes.SIGNIN_RATE_LIMIT
import app.logdate.server.routes.SIGNUP_RATE_LIMIT
import app.logdate.server.routes.sync.BACKUP_UPLOAD_RATE_LIMIT
import app.logdate.server.routes.sync.DEFAULT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.DRAFT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MAX_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MEDIA_UPLOAD_RATE_LIMIT

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 60 * 60

/**
 * The numbers the reference quotes, taken from the constants the handlers enforce. Prose refers to
 * them through `{{placeholders}}` so a limit can never be changed in code without the docs
 * following.
 */
internal object ApiLimits {
    val placeholders: Map<String, String> =
        mapOf(
            "auth.signup" to describe(SIGNUP_RATE_LIMIT),
            "auth.signin" to describe(SIGNIN_RATE_LIMIT),
            "media.upload" to describe(MEDIA_UPLOAD_RATE_LIMIT),
            "backup.upload" to describe(BACKUP_UPLOAD_RATE_LIMIT),
            "transcription.sessions" to describe(CLOUD_TRANSCRIPTION_SESSION_LIMIT),
            "sync.limit.default" to DEFAULT_SYNC_PAGE_SIZE.toString(),
            "sync.limit.max" to MAX_SYNC_PAGE_SIZE.toString(),
            "drafts.limit.default" to DRAFT_SYNC_PAGE_SIZE.toString(),
        )

    /** `RateLimitPolicy(5, 3600)` → `5 requests per hour`. */
    fun describe(policy: RateLimitPolicy): String {
        val window =
            when (policy.windowSeconds) {
                SECONDS_PER_MINUTE -> "minute"
                SECONDS_PER_HOUR -> "hour"
                else -> "${policy.windowSeconds} seconds"
            }
        val noun = if (policy.maxRequests == 1) "request" else "requests"
        return "${policy.maxRequests} $noun per $window"
    }

    /** `RateLimitPolicy(5, 3600)` → `one-hour`, for "wait for the one-hour window to pass". */
    fun windowName(policy: RateLimitPolicy): String =
        when (policy.windowSeconds) {
            SECONDS_PER_MINUTE -> "one-minute"
            SECONDS_PER_HOUR -> "one-hour"
            else -> "${policy.windowSeconds}-second"
        }
}

private val placeholderPattern = Regex("""\{\{([a-zA-Z0-9_.]+)}}""")

/** Replaces every `{{name}}` in [text] with its value from [ApiLimits]; unknown names fail loudly. */
internal fun renderApiText(text: String): String =
    placeholderPattern.replace(text) { match ->
        val key = match.groupValues[1]
        ApiLimits.placeholders[key] ?: error("Unknown documentation placeholder {{$key}}")
    }
