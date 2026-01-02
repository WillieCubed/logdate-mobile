package app.logdate.client.intelligence.cache

interface AICacheKeyStrategy {
    fun createKey(input: AICacheKeyInput): AICacheKey
}

data class AICacheKeyInput(
    val contentType: GenerativeAICacheContentType,
    val inputText: String,
    val providerId: String?,
    val model: String?,
    val promptVersion: String,
    val schemaVersion: String,
    val templateId: String?,
    val policy: AICachePolicy,
)

data class AICacheKey(
    val value: String,
    val sourceHash: String,
    val debugPrefix: String,
)

interface AICacheHasher {
    fun hash(value: String): String
}

object Fnv1aAICacheHasher : AICacheHasher {
    private const val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL
    private const val FNV_PRIME = 0x100000001b3uL

    override fun hash(value: String): String {
        var hash = FNV_OFFSET_BASIS
        value.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= FNV_PRIME
        }
        return hash.toString(16).padStart(16, '0')
    }
}

class DefaultAICacheKeyStrategy(
    private val hasher: AICacheHasher = Fnv1aAICacheHasher,
) : AICacheKeyStrategy {
    private val unsafeChars = Regex("[^A-Za-z0-9._-]")

    override fun createKey(input: AICacheKeyInput): AICacheKey {
        val normalizedInput = normalizeInput(input.inputText)
        val sourceHash = hasher.hash(normalizedInput)
        val debugPrefix = sanitize(
            listOf(
                input.contentType.id,
                input.providerId ?: "unknown",
                input.model ?: "default",
                input.promptVersion,
                input.schemaVersion,
                input.templateId ?: "default",
            ).joinToString("-")
        )

        val keyParts = buildList {
            add(input.contentType.id)
            if (input.policy.includeProviderInKey) {
                add(input.providerId ?: "unknown")
            }
            if (input.policy.includeModelInKey) {
                add(input.model ?: "default")
            }
            if (input.policy.includePromptVersionInKey) {
                add(input.promptVersion)
            }
            if (input.policy.includeSchemaVersionInKey) {
                add(input.schemaVersion)
            }
            if (input.policy.includeTemplateIdInKey) {
                add(input.templateId ?: "default")
            }
            add(normalizedInput)
        }

        val keyHash = hasher.hash(keyParts.joinToString("|"))
        val key = "${debugPrefix.take(MAX_PREFIX_LENGTH)}-$keyHash"
        return AICacheKey(value = key, sourceHash = sourceHash, debugPrefix = debugPrefix)
    }

    private fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        return trimmed.replace(Regex("\\s+"), " ")
    }

    private fun sanitize(value: String): String = value.replace(unsafeChars, "_")

    private companion object {
        private const val MAX_PREFIX_LENGTH = 60
    }
}
