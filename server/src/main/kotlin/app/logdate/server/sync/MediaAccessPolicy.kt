package app.logdate.server.sync

data class MediaAccessPolicy(
    val useSignedUrls: Boolean,
    val signedUrlTtlHours: Long,
) {
    companion object {
        private const val DEFAULT_TTL_HOURS = 1L
        private const val MAX_TTL_HOURS = 24L

        fun fromEnvironment(readEnv: (String) -> String? = System::getenv): MediaAccessPolicy {
            val useSignedUrls = readBooleanEnv("SYNC_MEDIA_SIGNED_URLS", defaultValue = false, readEnv = readEnv)
            val ttlHours =
                readEnv("SYNC_MEDIA_SIGNED_URL_TTL_HOURS")
                    ?.toLongOrNull()
                    ?.coerceIn(1L, MAX_TTL_HOURS)
                    ?: DEFAULT_TTL_HOURS
            return MediaAccessPolicy(useSignedUrls, ttlHours)
        }

        private fun readBooleanEnv(
            name: String,
            defaultValue: Boolean,
            readEnv: (String) -> String?,
        ): Boolean {
            val raw = readEnv(name) ?: return defaultValue
            return raw.equals("true", ignoreCase = true) ||
                raw.equals("yes", ignoreCase = true) ||
                raw == "1"
        }
    }
}
