package app.logdate.server.crypto

enum class EncryptionMode {
    AT_REST_ONLY,
    E2EE_REQUIRED;

    companion object {
        fun fromEnvironment(): EncryptionMode {
            val mode = System.getenv("ENCRYPTION_MODE") ?: "AT_REST_ONLY"
            return valueOf(mode)
        }
    }
}
