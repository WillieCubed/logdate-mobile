package app.logdate.shared.model

import kotlinx.serialization.Serializable
import app.logdate.util.UuidSerializer
import kotlin.uuid.Uuid

/**
 * Represents a unique identifier for an editor instance.
 *
 * This is used to track different editor windows in multi-window mode.
 */
@Serializable
data class EditorInstanceId(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid
)