package app.logdate.atproto.identity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf

internal fun mockHttpClient(response: () -> Pair<HttpStatusCode, String>): HttpClient =
    HttpClient(
        MockEngine {
            val (status, body) = response()
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        },
    )

internal fun mockHttpClient(response: (String) -> Pair<HttpStatusCode, String>): HttpClient =
    HttpClient(
        MockEngine { request ->
            val (status, body) = response(request.url.fullPath)
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        },
    )
