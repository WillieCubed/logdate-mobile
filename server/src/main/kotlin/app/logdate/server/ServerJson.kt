package app.logdate.server

import app.logdate.util.UuidSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The JSON configuration every route uses for request and response bodies. */
@OptIn(ExperimentalUuidApi::class)
internal val serverJson: Json =
    Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        serializersModule =
            SerializersModule {
                contextual(Uuid::class, UuidSerializer)
            }
    }

internal fun Application.installJsonSerialization() {
    install(ContentNegotiation) {
        json(serverJson)
    }
}
