package app.logdate.integration.e2e.fixtures

import app.logdate.client.sync.cloud.CloudApiException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

fun <T> assertCloudError(
    result: Result<T>,
    expectedCode: String,
    expectedStatus: Int? = null,
) {
    assertTrue(result.isFailure, "Expected failure result")
    val exception = assertIs<CloudApiException>(result.exceptionOrNull())
    assertEquals(expectedCode, exception.errorCode)
    if (expectedStatus != null) {
        assertEquals(expectedStatus, exception.statusCode)
    }
}
