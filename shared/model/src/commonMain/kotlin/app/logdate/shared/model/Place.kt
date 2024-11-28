package app.logdate.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
sealed class Place(
    val uid: Uuid,
    val name: String,
)