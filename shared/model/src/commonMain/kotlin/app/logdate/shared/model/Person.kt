package app.logdate.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Person(
    val uid: Uuid = Uuid.random(), // Allow entities to have the same name
    val name: String,
    val photoUri: String? = null,
    val aliases: List<String> = listOf(),
)