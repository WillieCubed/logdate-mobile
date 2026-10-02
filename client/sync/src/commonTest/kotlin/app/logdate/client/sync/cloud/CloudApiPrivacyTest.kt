package app.logdate.client.sync.cloud

import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudApiPrivacyTest {
    @Test
    fun `known media failures remain actionable while unknown server codes stay generic`() {
        assertEquals("MEDIA_ENCRYPT_FAILED", safeCloudErrorCode("MEDIA_ENCRYPT_FAILED"))
        assertEquals("MEDIA_UPLOAD_FAILED", safeCloudErrorCode("MEDIA_UPLOAD_FAILED"))
        assertEquals("MEDIA_METADATA_WRITE_FAILED", safeCloudErrorCode("MEDIA_METADATA_WRITE_FAILED"))
        assertEquals("UNKNOWN_ERROR", safeCloudErrorCode("private-journal-body"))
    }

    @Test
    fun `error bodies token prefixes and exception causes cannot escape through logs or API errors`() =
        runTest {
            val captured = mutableListOf<String>()
            val sink =
                object : Antilog() {
                    override fun performLog(
                        priority: LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        captured += message.orEmpty() + throwable?.stackTraceToString().orEmpty()
                    }
                }
            Napier.base(sink)
            try {
                HttpClient(
                    MockEngine {
                        respond(
                            """{"code":"SERVER_ERROR","message":"private-journal-body"}""",
                            HttpStatusCode.ServiceUnavailable,
                        )
                    },
                ).use { http ->
                    val api = LogDateCloudApiClient(DefaultLogDateConfigRepository(), http)
                    val result = api.getAccountInfo("tokensecret")
                    assertTrue(result.isFailure)
                    assertFalse(result.exceptionOrNull().toString().contains("private-"))
                }
                HttpClient(MockEngine { throw IllegalStateException("private-path-and-token") }).use { http ->
                    val result = LogDateCloudApiClient(DefaultLogDateConfigRepository(), http).getAccountInfo("tokensecret")
                    assertFalse(
                        result
                            .exceptionOrNull()
                            ?.stackTraceToString()
                            .orEmpty()
                            .contains("private-"),
                    )
                }
                assertFalse(captured.joinToString().contains("private-"))
                assertFalse(captured.joinToString().contains("token" + "s"))
            } finally {
                Napier.takeLogarithm(sink)
            }
        }
}
