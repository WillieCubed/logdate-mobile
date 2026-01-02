package app.logdate.client.intelligence.cache

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface AICacheEntryCodec {
    fun encode(entry: GenerativeAICacheEntry): String
    fun decode(raw: String): GenerativeAICacheEntry?
}

interface AICacheEntryCompressor {
    fun compress(raw: String): String
    fun decompress(raw: String): String
}

object NoOpAICacheEntryCompressor : AICacheEntryCompressor {
    override fun compress(raw: String): String = raw

    override fun decompress(raw: String): String = raw
}

interface AICacheCipher {
    fun encrypt(raw: String): String
    fun decrypt(raw: String): String
}

object NoOpAICacheCipher : AICacheCipher {
    override fun encrypt(raw: String): String = raw

    override fun decrypt(raw: String): String = raw
}

class JsonAICacheEntryCodec(
    private val cipher: AICacheCipher = NoOpAICacheCipher,
    private val compressor: AICacheEntryCompressor = NoOpAICacheEntryCompressor,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    },
) : AICacheEntryCodec {
    override fun encode(entry: GenerativeAICacheEntry): String {
        val payload = json.encodeToString(entry)
        return cipher.encrypt(compressor.compress(payload))
    }

    override fun decode(raw: String): GenerativeAICacheEntry? {
        return runCatching {
            json.decodeFromString<GenerativeAICacheEntry>(compressor.decompress(cipher.decrypt(raw)))
        }
            .getOrNull()
    }
}
