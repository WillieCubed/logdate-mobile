package app.logdate.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class Person(
    val uid: Uuid = Uuid.random(), // Allow entities to have the game name
    val name: String,
    val aliases: List<String> = listOf(),
)