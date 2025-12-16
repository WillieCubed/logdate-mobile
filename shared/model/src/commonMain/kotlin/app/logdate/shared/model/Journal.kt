package app.logdate.shared.model

import app.logdate.util.UuidSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlin.uuid.Uuid

@Serializable
data class Journal(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val description: String = "",
    val isFavorited: Boolean = false, // TODO: Move to separate user journal data repository
    val created: Instant = Clock.System.now(),
    val lastUpdated: Instant = Clock.System.now(),
)
